package com.company.ordertracking.security;

import com.company.ordertracking.entity.AppUser;
import com.company.ordertracking.repo.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminRunner implements CommandLineRunner {

    private final AppUserRepository repo;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.username:admin}")
    private String adminUsername;

    @Value("${app.bootstrap.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.role:GM}")
    private String adminRole;

    public BootstrapAdminRunner(AppUserRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminUsername == null || adminUsername.isBlank()) return;

        repo.findByUsername(adminUsername).ifPresentOrElse(
                u -> {
                    // exists - do nothing
                },
                () -> {
                    AppUser u = new AppUser();
                    u.setUsername(adminUsername.trim());
                    u.setRole((adminRole == null || adminRole.isBlank()) ? "GM" : adminRole.trim().toUpperCase());
                    u.setEnabled(true);
                    u.setPasswordHash(passwordEncoder.encode(adminPassword == null ? "admin123" : adminPassword));
                    repo.save(u);
                    System.out.println("[BOOTSTRAP] Created admin user: " + u.getUsername() + " (role=" + u.getRole() + ")");
                }
        );
    }
}
