package io.sentinel.backend.security;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/**
 * Creates default admin user on startup if no users exist.
 * Uses BCryptPasswordEncoder so the hash is always correct.
 * Default credentials: admin / Admin@123 — CHANGE IN PRODUCTION.
 */
@Configuration
public class DataInitializer {

    private final UserRepository    users;
    private final PasswordEncoder   encoder;

    public DataInitializer(UserRepository users, PasswordEncoder encoder) {
        this.users   = users;
        this.encoder = encoder;
    }

    @Bean
    public ApplicationRunner initAdminUser() {
        return args -> {
            if (users.existsByUsername("admin")) {
                System.out.println("[DataInitializer] Admin user already exists — skipping.");
                return;
            }
            UserEntity admin = new UserEntity();
            admin.id           = UUID.randomUUID().toString();
            admin.username     = "admin";
            admin.email        = "admin@sentinel.ai";
            admin.passwordHash = encoder.encode("Admin@123");
            admin.fullName     = "System Administrator";
            admin.role         = Role.ADMIN;
            admin.tenantId     = "default";
            admin.active       = true;
            users.save(admin);
            System.out.println("[DataInitializer] Admin user created — username: admin, password: Admin@123");
            System.out.println("[DataInitializer] CHANGE THIS PASSWORD IN PRODUCTION!");
        };
    }

    // Convenience method to promote a user to ADMIN (for development)
    public void promoteToAdmin(String username) {
        users.findByUsername(username).ifPresent(u -> {
            u.role = Role.ADMIN;
            users.save(u);
            System.out.println("[DataInitializer] Promoted " + username + " to ADMIN");
        });
    }
}