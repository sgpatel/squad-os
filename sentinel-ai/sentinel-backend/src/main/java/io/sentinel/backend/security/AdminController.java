package io.sentinel.backend.security;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/admin")
// NOTE: /api/admin/setup is public for initial setup only
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public AdminController(UserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }
    @GetMapping("/users")
    public List<Map<String,Object>> listUsers() {
        return users.findAll().stream().map(u -> Map.<String,Object>of(
            "id", u.id, "username", u.username, "email", u.email,
            "role", u.role.name(), "active", u.active,
            "tenantId", u.tenantId)).toList();
    }
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable String id,
        @RequestBody Map<String,String> body) {
        return users.findById(id).map(u -> {
            u.role = Role.valueOf(body.get("role").toUpperCase());
            users.save(u);
            return ResponseEntity.ok(Map.of("updated", true, "role", u.role.name()));
        }).orElse(ResponseEntity.notFound().build());
    }
    @PutMapping("/users/{id}/toggle")
    public ResponseEntity<?> toggleUser(@PathVariable String id) {
        return users.findById(id).map(u -> {
            u.active = !u.active;
            users.save(u);
            return ResponseEntity.ok(Map.of("active", u.active));
        }).orElse(ResponseEntity.notFound().build());
    }
    // ── Dev helper: promote any user to ADMIN via H2 console or this endpoint ──
    // This endpoint is ADMIN-only — to bootstrap use DataInitializer or H2 console:
    // UPDATE users SET role = 'ADMIN' WHERE username = 'yourname';
    @PutMapping("/users/{username}/promote")
    public ResponseEntity<?> promote(@PathVariable String username) {
        return users.findByUsername(username).map(u -> {
            u.role = Role.ADMIN;
            users.save(u);
            return ResponseEntity.ok(Map.of("promoted", true, "username", u.username, "role", u.role.name()));
        }).orElse(ResponseEntity.notFound().build());
    }
}