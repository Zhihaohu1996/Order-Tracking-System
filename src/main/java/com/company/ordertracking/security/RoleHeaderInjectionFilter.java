package com.company.ordertracking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class RoleHeaderInjectionFilter extends OncePerRequestFilter {

    public static final String HEADER_ROLE = "X-ROLE";
    public static final String HEADER_USER = "X-USER";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityUtil.authentication().orElse(null);
        if (auth == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .orElse("GUEST");

        String username = auth.getName();

        HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (name == null) return super.getHeader(null);
                if (HEADER_ROLE.equalsIgnoreCase(name)) return role;
                if (HEADER_USER.equalsIgnoreCase(name)) return username;
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (name != null && HEADER_ROLE.equalsIgnoreCase(name)) {
                    return Collections.enumeration(Collections.singletonList(role));
                }
                if (name != null && HEADER_USER.equalsIgnoreCase(name)) {
                    return Collections.enumeration(Collections.singletonList(username));
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Set<String> names = new HashSet<>();
                Enumeration<String> e = super.getHeaderNames();
                while (e.hasMoreElements()) names.add(e.nextElement());
                names.add(HEADER_ROLE);
                names.add(HEADER_USER);
                return Collections.enumeration(names);
            }
        };

        filterChain.doFilter(wrapped, response);
    }
}
