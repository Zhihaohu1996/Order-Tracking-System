package com.company.ordertracking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtil {

    private SecurityUtil() {}

    public static Optional<Authentication> authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        // AnonymousAuthenticationToken isAuthenticated==true; filter it out by principal name
        if ("anonymousUser".equals(String.valueOf(auth.getPrincipal()))) return Optional.empty();
        return Optional.of(auth);
    }

    public static String usernameOrNull() {
        return authentication().map(Authentication::getName).orElse(null);
    }

    public static String roleOrNull() {
        return authentication()
                .flatMap(a -> a.getAuthorities().stream().findFirst())
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.startsWith("ROLE_") ? r.substring("ROLE_".length()) : r)
                .orElse(null);
    }
}
