package com.company.ordertracking.web;

import com.company.ordertracking.audit.AuditLogService;
import com.company.ordertracking.entity.AuditLog;
import com.company.ordertracking.repo.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService service;
    private final AuditLogRepository repo;

    public AuditLogController(AuditLogService service, AuditLogRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    private static Map<String, Object> toDto(AuditLog a) {
        return Map.of(
                "id", a.getId(),
                "createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "username", a.getUsername(),
                "role", a.getRole(),
                "action", a.getAction(),
                "target", a.getTarget(),
                "status", a.getStatus(),
                "ip", a.getIp(),
                "details", a.getDetails()
        );
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int p = Math.max(0, page);
        int s = Math.min(200, Math.max(1, size));
        PageRequest pr = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AuditLog> result = service.search(q, action, username, status, from, to, pr);
        List<Map<String, Object>> content = result.getContent().stream().map(AuditLogController::toDto).toList();
        return ResponseEntity.ok(Map.of(
                "content", content,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("message", "NOT_FOUND"));
        }
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "OK"));
    }
}
