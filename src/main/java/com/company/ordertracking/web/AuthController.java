package com.company.ordertracking.web;

import com.company.ordertracking.security.SecurityUtil;
import com.company.ordertracking.audit.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AuditLogService auditLogService;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          AuditLogService auditLogService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.auditLogService = auditLogService;
    }

    public static class LoginRequest {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username, req.password)
            );
        } catch (Exception ex) {
            // login fail (no session)
            auditLogService.logWithUsername(request,
                    req == null ? null : req.username,
                    null,
                    "LOGIN",
                    req == null ? null : req.username,
                    AuditLogService.Status.FAIL,
                    ex.getClass().getSimpleName());
            return ResponseEntity.status(401).body(Map.of("message", "LOGIN_FAILED"));
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        auditLogService.log(request, "LOGIN", SecurityUtil.usernameOrNull(), AuditLogService.Status.SUCCESS,
                "role=" + SecurityUtil.roleOrNull());

        return ResponseEntity.ok(meBody());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        if (SecurityUtil.usernameOrNull() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }
        return ResponseEntity.ok(meBody());
    }

    private Map<String, Object> meBody() {
        return Map.of(
                "username", SecurityUtil.usernameOrNull(),
                "role", SecurityUtil.roleOrNull()
        );
    }
}
