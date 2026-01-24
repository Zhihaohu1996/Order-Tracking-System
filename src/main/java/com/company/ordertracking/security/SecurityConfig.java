package com.company.ordertracking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository repo,
                                                   LogoutSuccessHandler logoutSuccessHandler) throws Exception {
        // We use a JSON login endpoint (/api/auth/login) so we disable the default login UI.
        http.csrf(csrf -> csrf.disable());
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());

        http.securityContext(sc -> sc.securityContextRepository(repo));
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        http.exceptionHandling(eh -> eh.authenticationEntryPoint((req, res, ex) -> {
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"message\":\"UNAUTHORIZED\"}");
        }));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/"), new AntPathRequestMatcher("/index.html"))
                .permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/auth/**"))
                .permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/audit-logs/**"))
                .hasRole("GM")
                .requestMatchers(new AntPathRequestMatcher("/api/admin/**"))
                .hasRole("GM")
                .requestMatchers(new AntPathRequestMatcher("/api/orders/import/**"))
                .hasAnyRole("GM", "SALES")
                .anyRequest()
                .authenticated()
        );

        // Backwards compatibility: existing controllers may still read X-ROLE header.
        // This filter injects X-ROLE based on the authenticated session.
        http.addFilterAfter(new RoleHeaderInjectionFilter(), SecurityContextHolderFilter.class);

        // Session logout endpoint for the frontend
        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(logoutSuccessHandler)
        );

        return http.build();
    }
}
