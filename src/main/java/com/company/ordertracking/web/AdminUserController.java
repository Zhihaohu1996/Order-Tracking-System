package com.company.ordertracking.web;

import com.company.ordertracking.entity.AppUser;
import com.company.ordertracking.repo.AppUserRepository;
import com.company.ordertracking.audit.AuditLogService;
import com.company.ordertracking.security.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AppUserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AdminUserController(AppUserRepository repo,
                               PasswordEncoder passwordEncoder,
                               AuditLogService auditLogService) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public static class CreateUserRequest {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
        @NotBlank
        public String role;
        public Boolean enabled = true;
    }

    public static class ResetPasswordRequest {
        @NotBlank
        public String password;
    }

    private static Map<String, Object> toDto(AppUser u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "role", u.getRole(),
                "enabled", u.getEnabled(),
                "createdAt", u.getCreatedAt() == null ? null : u.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return repo.findAll().stream().map(AdminUserController::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateUserRequest req,
                                    jakarta.servlet.http.HttpServletRequest httpReq) {
        String username = req.username.trim();
        if (repo.findByUsername(username).isPresent()) {
            auditLogService.log(httpReq, "ADMIN_CREATE_USER", username, AuditLogService.Status.FAIL, "USERNAME_EXISTS");
            return ResponseEntity.badRequest().body(Map.of("message", "USERNAME_EXISTS"));
        }

        AppUser u = new AppUser();
        u.setUsername(username);
        u.setRole(req.role.trim().toUpperCase());
        u.setEnabled(req.enabled == null || req.enabled);
        u.setPasswordHash(passwordEncoder.encode(req.password));

        repo.save(u);
        auditLogService.log(httpReq, "ADMIN_CREATE_USER", username, AuditLogService.Status.SUCCESS, "role=" + u.getRole());
        return ResponseEntity.ok(toDto(u));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest req,
                                           jakarta.servlet.http.HttpServletRequest httpReq) {
        AppUser u = repo.findById(id).orElse(null);
        if (u == null) {
            auditLogService.log(httpReq, "ADMIN_RESET_PASSWORD", String.valueOf(id), AuditLogService.Status.FAIL, "NOT_FOUND");
            return ResponseEntity.status(404).body(Map.of("message", "NOT_FOUND"));
        }
        u.setPasswordHash(passwordEncoder.encode(req.password));
        repo.save(u);
        auditLogService.log(httpReq, "ADMIN_RESET_PASSWORD", u.getUsername(), AuditLogService.Status.SUCCESS, "id=" + id);
        return ResponseEntity.ok(Map.of("message", "OK"));
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<?> setEnabled(@PathVariable Long id,
                                        @RequestBody Map<String, Object> body,
                                        jakarta.servlet.http.HttpServletRequest httpReq) {
        AppUser u = repo.findById(id).orElse(null);
        if (u == null) {
            auditLogService.log(httpReq, "ADMIN_SET_ENABLED", String.valueOf(id), AuditLogService.Status.FAIL, "NOT_FOUND");
            return ResponseEntity.status(404).body(Map.of("message", "NOT_FOUND"));
        }
        Object v = body.get("enabled");
        boolean enabled = !(v instanceof Boolean b) || b;
        u.setEnabled(enabled);
        repo.save(u);
        auditLogService.log(httpReq, "ADMIN_SET_ENABLED", u.getUsername(), AuditLogService.Status.SUCCESS, "enabled=" + enabled);
        return ResponseEntity.ok(Map.of("message", "OK"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    jakarta.servlet.http.HttpServletRequest httpReq) {
        AppUser u = repo.findById(id).orElse(null);
        if (u == null) {
            auditLogService.log(httpReq, "ADMIN_DELETE_USER", String.valueOf(id), AuditLogService.Status.FAIL, "NOT_FOUND");
            return ResponseEntity.status(404).body(Map.of("message", "NOT_FOUND"));
        }
        // Prevent deleting yourself
        String me = SecurityUtil.usernameOrNull();
        if (me != null && me.equals(u.getUsername())) {
            auditLogService.log(httpReq, "ADMIN_DELETE_USER", u.getUsername(), AuditLogService.Status.FAIL, "CANNOT_DELETE_SELF");
            return ResponseEntity.badRequest().body(Map.of("message", "CANNOT_DELETE_SELF"));
        }
        repo.delete(u);
        auditLogService.log(httpReq, "ADMIN_DELETE_USER", u.getUsername(), AuditLogService.Status.SUCCESS, "id=" + id);
        return ResponseEntity.ok(Map.of("message", "OK"));
    }
}
