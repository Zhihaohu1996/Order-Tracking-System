package com.company.ordertracking.audit;

import com.company.ordertracking.entity.AuditLog;
import com.company.ordertracking.repo.AuditLogRepository;
import com.company.ordertracking.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuditLogService {

    private final AuditLogRepository repo;

    public AuditLogService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public enum Status {
        SUCCESS,
        FAIL
    }

    public void log(HttpServletRequest request, String action, String target, Status status, String details) {
        logWithPrincipal(request, SecurityUtil.usernameOrNull(), SecurityUtil.roleOrNull(), action, target, status, details);
    }

    public void logWithUsername(HttpServletRequest request, String username, String role, String action, String target, Status status, String details) {
        logWithPrincipal(request, username, role, action, target, status, details);
    }

    private void logWithPrincipal(HttpServletRequest request, String username, String role, String action, String target, Status status, String details) {
        try {
            AuditLog a = new AuditLog();
            a.setUsername(username);
            a.setRole(role);
            a.setAction(normalize(action));
            a.setTarget(target);
            a.setStatus(status == null ? Status.SUCCESS.name() : status.name());
            a.setIp(extractIp(request));
            a.setDetails(details);
            repo.save(a);
        } catch (Exception ignored) {
            // Do not break business flow if logging fails.
        }
    }

    private static String normalize(String s) {
        if (s == null) return "UNKNOWN";
        return s.trim().toUpperCase(Locale.ROOT);
    }

    private static String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            // take first
            String first = xf.split(",")[0].trim();
            return first.isEmpty() ? null : first;
        }
        String xr = request.getHeader("X-Real-IP");
        if (xr != null && !xr.isBlank()) return xr.trim();
        return request.getRemoteAddr();
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String q,
                                 String action,
                                 String username,
                                 String status,
                                 LocalDateTime from,
                                 LocalDateTime to,
                                 Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(null);
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), like),
                    cb.like(cb.lower(root.get("action")), like),
                    cb.like(cb.lower(root.get("target")), like),
                    cb.like(cb.lower(root.get("status")), like),
                    cb.like(cb.lower(root.get("ip")), like),
                    cb.like(cb.lower(root.get("details")), like)
            ));
        }
        if (action != null && !action.isBlank()) {
            String a = action.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), a));
        }
        if (username != null && !username.isBlank()) {
            String u = username.trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("username"), u));
        }
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), s));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return repo.findAll(spec, pageable);
    }
}
