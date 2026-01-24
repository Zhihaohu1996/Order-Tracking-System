package com.company.ordertracking.web;

import com.company.ordertracking.entity.*;
import com.company.ordertracking.repo.*;
import com.company.ordertracking.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final SalesOrderRepository orderRepo;
    private final ProductionPlanRepository planRepo;
    private final MaterialAssessmentRepository materialRepo;
    private final OrderProcessRepository processRepo;
    private final WarehouseReceiptRepository receiptRepo;
    private final WarehouseReceiptLogRepository receiptLogRepo;
    private final WarehouseReceiptLogItemRepository receiptLogItemRepo;
    private final ShipmentRepository shipmentRepo;
    private final AuditLogService auditLogService;

    public OrderController(
            SalesOrderRepository orderRepo,
            ProductionPlanRepository planRepo,
            MaterialAssessmentRepository materialRepo,
            OrderProcessRepository processRepo,
            WarehouseReceiptRepository receiptRepo,
            WarehouseReceiptLogRepository receiptLogRepo,
            WarehouseReceiptLogItemRepository receiptLogItemRepo,
            ShipmentRepository shipmentRepo,
            AuditLogService auditLogService
    ) {
        this.orderRepo = orderRepo;
        this.planRepo = planRepo;
        this.materialRepo = materialRepo;
        this.processRepo = processRepo;
        this.receiptRepo = receiptRepo;
        this.receiptLogRepo = receiptLogRepo;
        this.receiptLogItemRepo = receiptLogItemRepo;
        this.shipmentRepo = shipmentRepo;
        this.auditLogService = auditLogService;
    }

    private Role roleOf(String xRole) {
        return Role.fromHeader(xRole);
    }

    private void require(boolean ok, HttpStatus status, String msg) {
        if (!ok) throw new ResponseStatusException(status, msg);
    }

    @GetMapping
    @Transactional
    public List<OrderResponse> list(@RequestHeader(value = "X-ROLE", required = false) String xRole) {
        Role role = roleOf(xRole);
        return orderRepo.findAll().stream()
                .sorted(Comparator.comparing(SalesOrder::getId))
                .map(o -> toResponse(o, role, false))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Transactional
    public OrderResponse get(@PathVariable Long id,
                             @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        Role role = roleOf(xRole);
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        return toResponse(so, role, true);
    }

    @PostMapping
    @Transactional
    public OrderResponse create(@Valid @RequestBody OrderRequest req,
                                @RequestHeader(value = "X-ROLE", required = false) String xRole,
                                HttpServletRequest httpReq) {
        Role role = roleOf(xRole);
        require(role.canEditOrderBasics(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = new SalesOrder();
        applyBasicFields(so, req);
        so.setStatus(OrderStatus.DRAFT);
        so.setCreatedAt(LocalDateTime.now());

        // items
        so.setItems(new ArrayList<>());
        if (req.items != null) {
            for (OrderRequest.Item it : req.items) {
                so.getItems().add(toEntityItem(so, it));
            }
        }

        SalesOrder saved = orderRepo.save(so);
        auditLogService.log(httpReq, "CREATE_ORDER", saved.getOrderNo(), AuditLogService.Status.SUCCESS,
                "orderId=" + saved.getId());
        return toResponse(saved, role, true);
    }

    @PutMapping("/{id}")
    @Transactional
    public OrderResponse update(@PathVariable Long id,
                                @Valid @RequestBody OrderRequest req,
                                @RequestHeader(value = "X-ROLE", required = false) String xRole,
                                HttpServletRequest httpReq) {
        Role role = roleOf(xRole);
        require(role.canEditOrderBasics(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        applyBasicFields(so, req);

        // status (optional)
        if (req.status != null && !req.status.isBlank()) {
            try {
                so.setStatus(OrderStatus.valueOf(req.status.trim().toUpperCase()));
            } catch (Exception ignore) {}
        }

        // replace items (but: once warehouse receipt logs exist, order items should not be changed)
        if (req.items != null) {
            boolean hasReceiptLogs = receiptLogRepo.existsByOrder_Id(so.getId());
            require(!hasReceiptLogs, HttpStatus.BAD_REQUEST,
                    "order items are locked after warehouse receipts are recorded");

            so.getItems().clear();
            for (OrderRequest.Item it : req.items) {
                so.getItems().add(toEntityItem(so, it));
            }
        }

        OrderResponse out = toResponse(so, role, true);
        auditLogService.log(httpReq, "UPDATE_ORDER", so.getOrderNo(), AuditLogService.Status.SUCCESS,
                "orderId=" + so.getId());
        return out;
    }

    // ===== Warehouse: multi receipts (can be partial / multiple times) =====
    @GetMapping("/{id}/warehouse-receipt-stats")
    @Transactional
    public List<OrderResponse.WarehouseReceiptStat> getReceiptStats(@PathVariable Long id,
                                                                    @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        // stats can be viewed by anyone (sensitive fields not included here)
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        Map<Long, Integer> receivedMap = new HashMap<>();
        for (Object[] row : receiptLogItemRepo.sumQtyByOrderItemId(so.getId())) {
            if (row == null || row.length < 2) continue;
            Long itemId = (Long) row[0];
            Number sum = (Number) row[1];
            receivedMap.put(itemId, sum == null ? 0 : sum.intValue());
        }

        List<OrderResponse.WarehouseReceiptStat> out = new ArrayList<>();
        for (OrderItem it : so.getItems()) {
            OrderResponse.WarehouseReceiptStat s = new OrderResponse.WarehouseReceiptStat();
            s.orderItemId = it.getId();
            s.productName = it.getProductName();
            s.spec = it.getSpec();
            s.demandQty = it.getQuantity() == null ? 0 : it.getQuantity();
            s.receivedQty = receivedMap.getOrDefault(it.getId(), 0);
            s.remainingQty = Math.max(0, (s.demandQty == null ? 0 : s.demandQty) - (s.receivedQty == null ? 0 : s.receivedQty));
            out.add(s);
        }

        return out;
    }

    @GetMapping("/{id}/warehouse-receipts")
    @Transactional
    public List<OrderResponse.WarehouseReceiptLog> listReceiptLogs(@PathVariable Long id,
                                                                   @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        return receiptLogRepo.findByOrder_IdOrderByReceivedAtDesc(so.getId()).stream()
                .map(this::toReceiptLogDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/warehouse-receipts")
    @Transactional
    public OrderResponse.WarehouseReceiptLog createReceiptLog(@PathVariable Long id,
                                                              @Valid @RequestBody WarehouseReceiptLogRequest req,
                                                              @RequestHeader(value = "X-ROLE", required = false) String xRole,
                                                              HttpServletRequest httpReq) {
        Role role = roleOf(xRole);
        require(role.canWarehouseOps(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        require(req != null && req.items != null && !req.items.isEmpty(), HttpStatus.BAD_REQUEST, "items required");

        // Build map for quick lookup
        Map<Long, OrderItem> itemMap = so.getItems().stream().collect(Collectors.toMap(OrderItem::getId, x -> x));

        WarehouseReceiptLog log = new WarehouseReceiptLog();
        log.setOrder(so);
        log.setReceivedAt(req.receivedAt != null ? req.receivedAt : LocalDateTime.now());
        log.setReceivedBy(req.receivedBy);
        log.setNote(req.note);

        List<WarehouseReceiptLogItem> items = new ArrayList<>();
        for (WarehouseReceiptLogRequest.Item it : req.items) {
            if (it == null) continue;
            OrderItem oi = itemMap.get(it.orderItemId);
            require(oi != null, HttpStatus.BAD_REQUEST, "order item not found: " + it.orderItemId);
            int qty = it.qty == null ? 0 : it.qty;
            require(qty > 0, HttpStatus.BAD_REQUEST, "qty must be > 0");

            WarehouseReceiptLogItem li = new WarehouseReceiptLogItem();
            li.setOrderItem(oi);
            li.setQty(qty);
            items.add(li);
        }
        require(!items.isEmpty(), HttpStatus.BAD_REQUEST, "no valid items");
        log.setItems(items);

        WarehouseReceiptLog saved = receiptLogRepo.save(log);
        auditLogService.log(httpReq, "WAREHOUSE_RECEIPT_LOG", so.getOrderNo(), AuditLogService.Status.SUCCESS,
                "orderId=" + so.getId() + ", logId=" + saved.getId());
        return toReceiptLogDto(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id,
                       @RequestHeader(value = "X-ROLE", required = false) String xRole,
                       HttpServletRequest httpReq) {
        Role role = roleOf(xRole);
        require(role.canDeleteOrder(), HttpStatus.FORBIDDEN, "role not allowed");
        SalesOrder so = orderRepo.findById(id).orElse(null);
        if (so == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        orderRepo.deleteById(id);
        auditLogService.log(httpReq, "DELETE_ORDER", so.getOrderNo(), AuditLogService.Status.SUCCESS,
                "orderId=" + id);
    }

    // ===== Workflow: production plan =====
    @GetMapping("/{id}/plan")
    @Transactional
    public OrderResponse.ProductionPlan getPlan(@PathVariable Long id,
                                                @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        Role role = roleOf(xRole);
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        ProductionPlan plan = planRepo.findByOrder_Id(so.getId()).orElse(null);
        return plan == null ? null : toPlanDto(plan);
    }

    @PutMapping("/{id}/plan")
    @Transactional
    public OrderResponse.ProductionPlan upsertPlan(@PathVariable Long id,
                                                   @RequestBody ProductionPlanRequest req,
                                                   @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        Role role = roleOf(xRole);
        require(role.canManageProductionPlan(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        ProductionPlan plan = planRepo.findByOrder_Id(so.getId()).orElse(new ProductionPlan());
        plan.setOrder(so);
        plan.setPlannedStartDate(req.plannedStartDate);
        plan.setPlannedEndDate(req.plannedEndDate);
        plan.setPlannedShipDate(req.plannedShipDate);
        plan.setNote(req.note);

        ProductionPlan saved = planRepo.save(plan);

        if (so.getStatus() == OrderStatus.DRAFT) {
            so.setStatus(OrderStatus.IN_PRODUCTION);
            }

        return toPlanDto(saved);
    }

    // ===== Workflow: materials (PMC) =====
    @GetMapping("/{id}/materials")
    @Transactional
    public List<OrderResponse.Material> getMaterials(@PathVariable Long id,
                                                     @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        Role role = roleOf(xRole);
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        return materialRepo.findByOrder_IdOrderByIdAsc(so.getId()).stream()
                .map(this::toMaterialDto)
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}/materials")
    @Transactional
    public List<OrderResponse.Material> replaceMaterials(@PathVariable Long id,
                                                         @Valid @RequestBody MaterialsRequest req,
                                                         @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        Role role = roleOf(xRole);
        require(role.canManageProductionPlan(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        materialRepo.deleteByOrder_Id(so.getId());
        if (req.materials != null) {
            for (MaterialsRequest.Material m : req.materials) {
                MaterialAssessment ma = new MaterialAssessment();
                ma.setOrder(so);
                ma.setMaterialName(m.materialName);
                ma.setProcurementType(m.procurementType);
                ma.setNote(m.note);
                materialRepo.save(ma);
            }
        }

        if (so.getStatus() == OrderStatus.DRAFT) {
            so.setStatus(OrderStatus.IN_PRODUCTION);
            }

        return materialRepo.findByOrder_IdOrderByIdAsc(so.getId()).stream()
                .map(this::toMaterialDto)
                .collect(Collectors.toList());
    }

    // ===== Workflow: processes (production) =====
    @GetMapping("/{id}/processes")
    @Transactional
    public List<OrderResponse.Process> getProcesses(@PathVariable Long id,
                                                    @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        return processRepo.findByOrder_IdOrderByIdAsc(so.getId()).stream()
                .map(this::toProcessDto)
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}/processes")
    @Transactional
    public List<OrderResponse.Process> replaceProcesses(@PathVariable Long id,
                                                        @Valid @RequestBody ProcessesRequest req,
                                                        @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        Role role = roleOf(xRole);
        require(role.canUpdateProcessProgress() || role.canManageProductionPlan(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        processRepo.deleteByOrder_Id(so.getId());
        if (req.processes != null) {
            for (ProcessesRequest.Process p : req.processes) {
                OrderProcess op = new OrderProcess();
                op.setOrder(so);
                op.setProcessName(p.processName);
                op.setTargetQuantity(p.targetQuantity);
                op.setFinishedQuantity(p.finishedQuantity == null ? 0 : Math.max(0, p.finishedQuantity));
                op.setNote(p.note);
                processRepo.save(op);
            }
        }

        if (so.getStatus() == OrderStatus.DRAFT) {
            so.setStatus(OrderStatus.IN_PRODUCTION);
            }

        return processRepo.findByOrder_IdOrderByIdAsc(so.getId()).stream()
                .map(this::toProcessDto)
                .collect(Collectors.toList());
    }

    // ===== Warehouse: receipt =====
    @PostMapping("/{id}/warehouse-receipt")
    @Transactional
    public OrderResponse.WarehouseReceipt confirmReceipt(@PathVariable Long id,
                                                         @RequestBody(required = false) WarehouseReceiptRequest req,
                                                         @RequestHeader(value = "X-ROLE", required = false) String xRole,
                                                         HttpServletRequest httpReq) {
        Role role = roleOf(xRole);
        require(role.canWarehouseOps(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        // all processes finished?
        List<OrderProcess> processes = processRepo.findByOrder_IdOrderByIdAsc(so.getId());
        boolean ok = !processes.isEmpty() && processes.stream().allMatch(p ->
                p.getTargetQuantity() == null || (p.getFinishedQuantity() != null && p.getFinishedQuantity() >= p.getTargetQuantity()));
        require(ok, HttpStatus.BAD_REQUEST, "processes not finished yet");

        WarehouseReceipt receipt = receiptRepo.findByOrder_Id(so.getId()).orElse(new WarehouseReceipt());
        receipt.setOrder(so);
        if (req != null) {
            receipt.setReceivedBy(req.receivedBy);
            receipt.setNote(req.note);
        }
        WarehouseReceipt saved = receiptRepo.save(receipt);

        so.setStatus(OrderStatus.READY_TO_SHIP);

        auditLogService.log(httpReq, "WAREHOUSE_RECEIPT_CONFIRM", so.getOrderNo(), AuditLogService.Status.SUCCESS,
                "orderId=" + so.getId());

        return toReceiptDto(saved);
    }

    // ===== Shipping =====
    @PutMapping("/{id}/shipment-plan")
    @Transactional
    public OrderResponse.Shipment setShipmentPlan(@PathVariable Long id,
                                                  @RequestBody ShipmentPlanRequest req,
                                                  @RequestHeader(value = "X-ROLE", required = false) String xRole) {
        Role role = roleOf(xRole);
        require(role.canManageShippingPlan(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        Shipment ship = shipmentRepo.findByOrder_Id(so.getId()).orElse(new Shipment());
        ship.setOrder(so);
        ship.setPlannedShipDate(req.plannedShipDate);
        ship.setNote(req.note);

        Shipment saved = shipmentRepo.save(ship);
        return toShipmentDto(saved);
    }

    @PostMapping("/{id}/ship")
    @Transactional
    public OrderResponse.Shipment confirmShipped(@PathVariable Long id,
                                                 @RequestBody(required = false) ShipConfirmRequest req,
                                                 @RequestHeader(value = "X-ROLE", required = false) String xRole,
                                                 HttpServletRequest httpReq) {
        Role role = roleOf(xRole);
        require(role.canWarehouseOps(), HttpStatus.FORBIDDEN, "role not allowed");

        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        require(so.getStatus() == OrderStatus.READY_TO_SHIP || so.getStatus() == OrderStatus.SHIPPED,
                HttpStatus.BAD_REQUEST, "order not ready to ship");

        Shipment ship = shipmentRepo.findByOrder_Id(so.getId()).orElse(new Shipment());
        ship.setOrder(so);
        ship.setShippedAt(LocalDateTime.now());
        if (req != null) {
            ship.setConfirmedBy(req.confirmedBy);
            ship.setNote(req.note);
        }
        Shipment saved = shipmentRepo.save(ship);

        so.setStatus(OrderStatus.ARCHIVED);

        auditLogService.log(httpReq, "SHIP_CONFIRM", so.getOrderNo(), AuditLogService.Status.SUCCESS,
                "orderId=" + so.getId() + ", shipmentId=" + saved.getId());

        return toShipmentDto(saved);
    }

    // ===== Helpers =====
    private void applyBasicFields(SalesOrder so, OrderRequest req) {
        so.setOrderNo(req.orderNo);
        so.setCurrency(req.currency);
        so.setCustomerName(req.customerName);
        so.setContact(req.contact);
        so.setPaymentTerms(req.paymentTerms);
        so.setTotalAmount(req.totalAmount);
        so.setProductReq(req.productReq);
        so.setPackagingReq(req.packagingReq);
    }

    private OrderItem toEntityItem(SalesOrder order, OrderRequest.Item it) {
        OrderItem ei = new OrderItem();
        ei.setOrder(order);
        ei.setProductName(it.productName);
        ei.setSpec(it.spec);
        ei.setQuantity(it.quantity == null ? 0 : it.quantity);
        ei.setUnitPrice(it.unitPrice == null ? 0.0 : it.unitPrice);
        ei.setNotes(it.notes);
        return ei;
    }

    private OrderResponse toResponse(SalesOrder so, Role role, boolean includeWorkflow) {
        OrderResponse r = new OrderResponse();
        r.id = so.getId();
        r.orderNo = so.getOrderNo();
        r.currency = so.getCurrency();
        r.customerName = so.getCustomerName();
        r.contact = so.getContact();
        r.paymentTerms = so.getPaymentTerms();
        r.totalAmount = so.getTotalAmount();
        r.productReq = so.getProductReq();
        r.packagingReq = so.getPackagingReq();
        r.status = so.getStatus();
        r.createdAt = so.getCreatedAt();

        r.items = new ArrayList<>();
        for (OrderItem it : so.getItems()) {
            OrderResponse.Item ii = new OrderResponse.Item();
            ii.id = it.getId();
            ii.productName = it.getProductName();
            ii.spec = it.getSpec();
            ii.quantity = it.getQuantity();
            ii.unitPrice = it.getUnitPrice();
            ii.notes = it.getNotes();
            r.items.add(ii);
        }

        if (!role.canSeeSensitive()) {
            r.currency = null;
            r.customerName = null;
            r.contact = null;
            r.paymentTerms = null;
            r.totalAmount = null;
            for (OrderResponse.Item ii : r.items) {
                ii.unitPrice = null;
            }
        }

        if (includeWorkflow) {
            planRepo.findByOrder_Id(so.getId()).ifPresent(p -> r.plan = toPlanDto(p));
            r.materials = materialRepo.findByOrder_IdOrderByIdAsc(so.getId()).stream()
                    .map(this::toMaterialDto).collect(Collectors.toList());
            r.processes = processRepo.findByOrder_IdOrderByIdAsc(so.getId()).stream()
                    .map(this::toProcessDto).collect(Collectors.toList());
            receiptRepo.findByOrder_Id(so.getId()).ifPresent(wr -> r.warehouseReceipt = toReceiptDto(wr));
            shipmentRepo.findByOrder_Id(so.getId()).ifPresent(s -> r.shipment = toShipmentDto(s));
        }

        return r;
    }

    private OrderResponse.ProductionPlan toPlanDto(ProductionPlan plan) {
        OrderResponse.ProductionPlan dto = new OrderResponse.ProductionPlan();
        dto.id = plan.getId();
        dto.plannedStartDate = plan.getPlannedStartDate();
        dto.plannedEndDate = plan.getPlannedEndDate();
        dto.plannedShipDate = plan.getPlannedShipDate();
        dto.note = plan.getNote();
        dto.updatedAt = plan.getUpdatedAt();
        return dto;
    }

    private OrderResponse.Material toMaterialDto(MaterialAssessment ma) {
        OrderResponse.Material dto = new OrderResponse.Material();
        dto.id = ma.getId();
        dto.materialName = ma.getMaterialName();
        dto.procurementType = ma.getProcurementType();
        dto.note = ma.getNote();
        dto.createdAt = ma.getCreatedAt();
        return dto;
    }

    private OrderResponse.Process toProcessDto(OrderProcess op) {
        OrderResponse.Process dto = new OrderResponse.Process();
        dto.id = op.getId();
        dto.processName = op.getProcessName();
        dto.targetQuantity = op.getTargetQuantity();
        dto.finishedQuantity = op.getFinishedQuantity();
        dto.note = op.getNote();
        dto.updatedAt = op.getUpdatedAt();
        return dto;
    }

    private OrderResponse.WarehouseReceipt toReceiptDto(WarehouseReceipt wr) {
        OrderResponse.WarehouseReceipt dto = new OrderResponse.WarehouseReceipt();
        dto.id = wr.getId();
        dto.receivedAt = wr.getReceivedAt();
        dto.receivedBy = wr.getReceivedBy();
        dto.note = wr.getNote();
        return dto;
    }

    private OrderResponse.WarehouseReceiptLog toReceiptLogDto(WarehouseReceiptLog log) {
        OrderResponse.WarehouseReceiptLog dto = new OrderResponse.WarehouseReceiptLog();
        dto.id = log.getId();
        dto.receivedAt = log.getReceivedAt();
        dto.receivedBy = log.getReceivedBy();
        dto.note = log.getNote();
        dto.items = new ArrayList<>();
        if (log.getItems() != null) {
            for (WarehouseReceiptLogItem it : log.getItems()) {
                OrderResponse.WarehouseReceiptLogItem d = new OrderResponse.WarehouseReceiptLogItem();
                d.orderItemId = it.getOrderItem() == null ? null : it.getOrderItem().getId();
                d.productName = it.getOrderItem() == null ? null : it.getOrderItem().getProductName();
                d.spec = it.getOrderItem() == null ? null : it.getOrderItem().getSpec();
                d.qty = it.getQty();
                dto.items.add(d);
            }
        }
        return dto;
    }

    private OrderResponse.Shipment toShipmentDto(Shipment s) {
        OrderResponse.Shipment dto = new OrderResponse.Shipment();
        dto.id = s.getId();
        dto.plannedShipDate = s.getPlannedShipDate();
        dto.shippedAt = s.getShippedAt();
        dto.confirmedBy = s.getConfirmedBy();
        dto.note = s.getNote();
        dto.updatedAt = s.getUpdatedAt();
        return dto;
    }
}
