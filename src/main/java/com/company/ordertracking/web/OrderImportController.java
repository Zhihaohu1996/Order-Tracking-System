package com.company.ordertracking.web;

import com.company.ordertracking.entity.OrderItem;
import com.company.ordertracking.entity.OrderStatus;
import com.company.ordertracking.entity.SalesOrder;
import com.company.ordertracking.audit.AuditLogService;
import com.company.ordertracking.repo.SalesOrderRepository;
import jakarta.transaction.Transactional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders/import")
public class OrderImportController {

    private final SalesOrderRepository salesOrderRepo;
    private final AuditLogService auditLogService;

    public OrderImportController(SalesOrderRepository salesOrderRepo,
                                AuditLogService auditLogService) {
        this.salesOrderRepo = salesOrderRepo;
        this.auditLogService = auditLogService;
    }

    public record TextImportRequest(String text, String delimiter) {}

    // Backward compatible field names:
    // - importedOrders / importedItems (backend)
    // - ordersImported / itemsImported (frontend older versions)
    public record ImportResult(int importedOrders, int importedItems,
                               int ordersImported, int itemsImported,
                               List<String> messages) {}

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> importFromFile(@RequestParam("file") MultipartFile file,
                                            jakarta.servlet.http.HttpServletRequest httpReq) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要上传的文件"));
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        List<ImportRow> rows;
        if (name.endsWith(".csv")) {
            rows = parseCsv(file.getInputStream());
        } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            rows = parseExcel(file.getInputStream());
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .csv / .xlsx / .xls 文件"));
        }

        return doImport(httpReq, rows);
    }

    @PostMapping(value = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> importFromText(@RequestBody TextImportRequest req,
                                            jakarta.servlet.http.HttpServletRequest httpReq) {
        String text = req == null ? null : req.text();
        if (!StringUtils.hasText(text)) {
            return ResponseEntity.badRequest().body(Map.of("error", "请粘贴表格内容"));
        }
        char delimiter = detectDelimiter(req.delimiter(), text);
        List<ImportRow> rows;
        try {
            rows = parseDelimitedText(text, delimiter);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return doImport(httpReq, rows);
    }

    // ---------------- core import ----------------

    private ResponseEntity<?> doImport(jakarta.servlet.http.HttpServletRequest httpReq, List<ImportRow> rows) {
        if (rows.isEmpty()) {
            auditLogService.log(httpReq, "IMPORT_ORDERS", null, AuditLogService.Status.FAIL, "no rows");
            return ResponseEntity.badRequest().body(Map.of("error", "没有读取到任何数据行"));
        }

        // validate required fields
        List<String> errors = new ArrayList<>();
        for (ImportRow r : rows) {
            if (!StringUtils.hasText(r.orderNo())) {
                errors.add("第 " + r.rowNumber() + " 行缺少 订单号(orderNo/订单号)");
            }
            if (r.hasBadNumber()) {
                errors.add("第 " + r.rowNumber() + " 行数字格式错误：数量/单价/总金额" );
            }
        }
        if (!errors.isEmpty()) {
            auditLogService.log(httpReq, "IMPORT_ORDERS", null, AuditLogService.Status.FAIL, "validation errors: " + errors.size());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "导入失败（请先修正表格后再导入）",
                    "details", errors
            ));
        }

        // group by orderNo
        Map<String, List<ImportRow>> byOrder = rows.stream()
                .collect(Collectors.groupingBy(r -> r.orderNo().trim(), LinkedHashMap::new, Collectors.toList()));

        // A: if ANY order already exists in DB => abort
        Set<String> orderNos = byOrder.keySet();
        List<SalesOrder> existing = salesOrderRepo.findByOrderNoIn(orderNos);
        if (!existing.isEmpty()) {
            List<String> dup = existing.stream().map(SalesOrder::getOrderNo).sorted().toList();
            auditLogService.log(httpReq, "IMPORT_ORDERS", null, AuditLogService.Status.FAIL, "duplicates: " + dup);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "导入失败：发现重复订单号（数据库已存在），已停止导入",
                    "duplicates", dup
            ));
        }

        int importedItems = 0;
        List<SalesOrder> toSave = new ArrayList<>();

        for (Map.Entry<String, List<ImportRow>> e : byOrder.entrySet()) {
            String orderNo = e.getKey();
            List<ImportRow> group = e.getValue();
            ImportRow first = group.get(0);

            SalesOrder o = new SalesOrder();
            o.setOrderNo(orderNo);
            o.setCustomerName(blankToNull(first.customerName()));
            // Note: In this project the entity field is named `contact`.
            o.setContact(blankToNull(first.customerContact()));
            o.setCurrency(blankToNull(first.currency()));
            o.setPaymentTerms(blankToNull(first.paymentTerms()));
            // Entity column is BigDecimal, while imports parse numeric totals as Double.
            if (first.totalAmount() != null) o.setTotalAmount(java.math.BigDecimal.valueOf(first.totalAmount()));
            // Entity field names are `productReq` / `packagingReq`.
            o.setProductReq(blankToNull(first.productRequirements()));
            o.setPackagingReq(blankToNull(first.packagingRequirements()));
            o.setStatus(OrderStatus.DRAFT);

            List<OrderItem> items = new ArrayList<>();
            for (ImportRow r : group) {
                if (!r.hasAnyItemField()) continue;
                OrderItem it = new OrderItem();
                // In this project OrderItem links to SalesOrder via `order`.
                it.setOrder(o);
                it.setProductName(blankToNull(r.productName()));
                it.setSpec(blankToNull(r.spec()));
                it.setQuantity(r.quantity());
                it.setUnitPrice(r.unitPrice());
                it.setNotes(blankToNull(r.remark()));
                items.add(it);
                importedItems++;
            }
            o.setItems(items);

            toSave.add(o);
        }

        salesOrderRepo.saveAll(toSave);

        auditLogService.log(httpReq, "IMPORT_ORDERS", null, AuditLogService.Status.SUCCESS,
                "orders=" + toSave.size() + ", items=" + importedItems);
        return ResponseEntity.ok(new ImportResult(toSave.size(), importedItems, toSave.size(), importedItems, List.of("导入成功")));
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ---------------- parsing ----------------

    private static char detectDelimiter(String requested, String text) {
        if (StringUtils.hasText(requested)) {
            String d = requested.trim();
            if (d.equalsIgnoreCase("tab") || d.equals("\\t")) return '\t';
            if (d.equals(",") || d.equals("comma")) return ',';
            if (d.equals(";") || d.equals("semicolon")) return ';';
            if (d.length() == 1) return d.charAt(0);
        }
        // auto detect: prefer tab if present
        int tabs = count(text, '\t');
        int commas = count(text, ',');
        return tabs >= commas ? '\t' : ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private List<ImportRow> parseDelimitedText(String text, char delimiter) {
        List<String> lines = Arrays.stream(text.split("\\r?\\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();
        if (lines.size() < 2) {
            throw new IllegalArgumentException("至少需要包含表头 + 1 行数据");
        }

        List<String> headers = splitLine(lines.get(0), delimiter);
        HeaderMap hm = HeaderMap.fromHeaders(headers);
        if (!hm.hasOrderNo()) {
            throw new IllegalArgumentException("表头必须包含 订单号(orderNo/订单号) 列");
        }

        List<ImportRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cols = splitLine(lines.get(i), delimiter);
            rows.add(hm.toRow(i + 1, cols));
        }
        return rows;
    }

    private static List<String> splitLine(String line, char delimiter) {
        // For pasted data we assume simple delimited text (Excel tab-separated or simple CSV without quotes)
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == delimiter && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(cur.toString());
        return parts;
    }

    private List<ImportRow> parseCsv(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            CSVParser parser = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(br);

            Map<String, Integer> headerMap = parser.getHeaderMap();
            List<String> headers = new ArrayList<>(headerMap.keySet());
            HeaderMap hm = HeaderMap.fromHeaders(headers);
            if (!hm.hasOrderNo()) {
                throw new IllegalArgumentException("CSV 表头必须包含 订单号(orderNo/订单号) 列");
            }

            List<ImportRow> rows = new ArrayList<>();
            int rowNumber = 1; // header is row 1
            for (CSVRecord rec : parser) {
                rowNumber++;
                List<String> cols = new ArrayList<>();
                for (String h : headers) {
                    cols.add(rec.isMapped(h) ? rec.get(h) : "");
                }
                rows.add(hm.toRow(rowNumber, cols));
            }
            return rows;
        }
    }

    private List<ImportRow> parseExcel(InputStream in) throws IOException {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) return List.of();

            DataFormatter fmt = new DataFormatter(Locale.ROOT);
            Iterator<Row> it = sheet.rowIterator();
            if (!it.hasNext()) return List.of();

            Row headerRow = it.next();
            List<String> headers = new ArrayList<>();
            int lastCell = headerRow.getLastCellNum();
            for (int i = 0; i < lastCell; i++) {
                Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                headers.add(cell == null ? "" : fmt.formatCellValue(cell));
            }
            HeaderMap hm = HeaderMap.fromHeaders(headers);
            if (!hm.hasOrderNo()) {
                throw new IllegalArgumentException("Excel 表头必须包含 订单号(orderNo/订单号) 列");
            }

            List<ImportRow> rows = new ArrayList<>();
            int rowNumber = 1;
            while (it.hasNext()) {
                Row r = it.next();
                rowNumber = r.getRowNum() + 1;
                List<String> cols = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    cols.add(cell == null ? "" : fmt.formatCellValue(cell));
                }
                // skip fully blank lines
                boolean allBlank = cols.stream().allMatch(c -> !StringUtils.hasText(c));
                if (allBlank) continue;
                rows.add(hm.toRow(rowNumber, cols));
            }
            return rows;
        }
    }

    // ---------------- header mapping ----------------

    private record HeaderMap(Map<String, Integer> indexByField) {

        boolean hasOrderNo() {
            return indexByField.containsKey("orderNo");
        }

        ImportRow toRow(int rowNumber, List<String> cols) {
            String orderNo = get(cols, "orderNo");
            Double totalAmount = parseDouble(get(cols, "totalAmount"));
            Integer qty = parseInt(get(cols, "quantity"));
            Double unitPrice = parseDouble(get(cols, "unitPrice"));

            boolean badNumber = false;
            if (isBadNumber(get(cols, "totalAmount"), totalAmount)) badNumber = true;
            if (isBadInt(get(cols, "quantity"), qty)) badNumber = true;
            if (isBadNumber(get(cols, "unitPrice"), unitPrice)) badNumber = true;

            return new ImportRow(
                    rowNumber,
                    orderNo,
                    get(cols, "customerName"),
                    get(cols, "customerContact"),
                    get(cols, "currency"),
                    get(cols, "paymentTerms"),
                    totalAmount,
                    get(cols, "productRequirements"),
                    get(cols, "packagingRequirements"),
                    get(cols, "productName"),
                    get(cols, "spec"),
                    qty,
                    unitPrice,
                    get(cols, "remark"),
                    badNumber
            );
        }

        private String get(List<String> cols, String field) {
            Integer idx = indexByField.get(field);
            if (idx == null || idx < 0 || idx >= cols.size()) return null;
            return cols.get(idx);
        }

        static HeaderMap fromHeaders(List<String> headers) {
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i);
                String key = normalizeHeader(h);
                if (key == null) continue;
                // keep first occurrence
                map.putIfAbsent(key, i);
            }
            return new HeaderMap(map);
        }

        private static String normalizeHeader(String raw) {
            if (raw == null) return null;
            String h = raw.trim();
            if (h.isEmpty()) return null;

            String n = h.toLowerCase(Locale.ROOT)
                    .replace(" ", "")
                    .replace("_", "")
                    .replace("-", "")
                    .replace("（", "(")
                    .replace("）", ")");

            // order
            if (n.equals("orderno") || n.equals("order#") || n.contains("订单号") || n.contains("订单编号")) return "orderNo";
            if (n.equals("customer") || n.equals("customername") || n.contains("客户") || n.contains("客户名称")) return "customerName";
            if (n.equals("contact") || n.equals("customercontact") || n.contains("联系人") || n.contains("联系方式")) return "customerContact";
            if (n.equals("currency") || n.contains("币种")) return "currency";
            if (n.equals("paymentterms") || n.contains("付款") || n.contains("条款")) return "paymentTerms";
            if (n.equals("total") || n.equals("totalamount") || n.contains("总金额") || n.equals("金额")) return "totalAmount";
            if (n.equals("productrequirements") || n.contains("产品要求") || n.contains("产品需求")) return "productRequirements";
            if (n.equals("packagingrequirements") || n.contains("包装要求")) return "packagingRequirements";

            // items
            if (n.equals("product") || n.equals("productname") || n.contains("产品") || n.contains("品名")) return "productName";
            if (n.equals("spec") || n.contains("规格") || n.contains("型号")) return "spec";
            if (n.equals("qty") || n.equals("quantity") || n.contains("数量")) return "quantity";
            if (n.equals("unitprice") || n.equals("price") || n.contains("单价")) return "unitPrice";
            if (n.equals("remark") || n.contains("备注")) return "remark";

            return null;
        }

        private static boolean isBadNumber(String raw, Double parsed) {
            return StringUtils.hasText(raw) && parsed == null;
        }

        private static boolean isBadInt(String raw, Integer parsed) {
            return StringUtils.hasText(raw) && parsed == null;
        }

        private static Double parseDouble(String raw) {
            if (!StringUtils.hasText(raw)) return null;
            String s = raw.trim()
                    .replace(",", "")
                    .replace("$", "")
                    .replace("￥", "")
                    .replace("¥", "");
            try {
                return Double.parseDouble(s);
            } catch (Exception e) {
                return null;
            }
        }

        private static Integer parseInt(String raw) {
            if (!StringUtils.hasText(raw)) return null;
            String s = raw.trim().replace(",", "");
            try {
                // allow "10.0" from Excel
                if (s.contains(".")) {
                    double d = Double.parseDouble(s);
                    return (int) Math.round(d);
                }
                return Integer.parseInt(s);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private record ImportRow(
            int rowNumber,
            String orderNo,
            String customerName,
            String customerContact,
            String currency,
            String paymentTerms,
            Double totalAmount,
            String productRequirements,
            String packagingRequirements,
            String productName,
            String spec,
            Integer quantity,
            Double unitPrice,
            String remark,
            boolean badNumber
    ) {
        boolean hasBadNumber() {
            return badNumber;
        }

        boolean hasAnyItemField() {
            return StringUtils.hasText(productName) || StringUtils.hasText(spec) || quantity != null || unitPrice != null || StringUtils.hasText(remark);
        }
    }
}
