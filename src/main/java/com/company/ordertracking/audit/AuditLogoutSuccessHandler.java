package com.company.ordertracking.audit;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuditLogoutSuccessHandler implements LogoutSuccessHandler {

    private final AuditLogService auditLogService;

    public AuditLogoutSuccessHandler(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        // At this point SecurityContext might be cleared; try to log with the still-available authentication.
        String username = authentication == null ? null : authentication.getName();
        String role = null;
        if (authentication != null && authentication.getAuthorities() != null) {
            role = authentication.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .filter(a -> a != null && a.startsWith("ROLE_"))
                    .findFirst()
                    .map(a -> a.substring("ROLE_".length()))
                    .orElse(null);
        }
        auditLogService.logWithUsername(request, username, role,
                "LOGOUT",
                username,
                AuditLogService.Status.SUCCESS,
                "logout");

        response.setStatus(200);
    }
}
