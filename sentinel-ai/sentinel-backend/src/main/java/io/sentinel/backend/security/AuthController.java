package io.sentinel.backend.security;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public AuthController(AuthenticationManager am, JwtService jwt,
        UserRepository users, PasswordEncoder encoder) {
        this.authManager = am; this.jwt = jwt;
        this.users = users; this.encoder = encoder;
    }

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {}

    public record RegisterRequest(
        @NotBlank @Size(min=3,max=50) String username,
        @Email @NotBlank String email,
        @NotBlank @Size(min=8) String password,
        String fullName) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
            UserEntity user = users.findByUsername(req.username())
                .orElseThrow(() -> new RuntimeException("User not found"));
            String token = jwt.generate(user);
            return ResponseEntity.ok(Map.of(
                "token", token,
                "username", user.username,
                "email", user.email,
                "role", user.role.name(),
                "tenantId", user.tenantId,
                "expiresAt", Instant.now().plusMillis(86400000).toString()
            ));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Username already taken"));
        }
        UserEntity user = new UserEntity();
        user.id           = UUID.randomUUID().toString();
        user.username     = req.username();
        user.email        = req.email();
        user.passwordHash = encoder.encode(req.password());
        user.fullName     = req.fullName() != null ? req.fullName() : req.username();
        user.role         = Role.REVIEWER; // default role
        user.tenantId     = "default";
        users.save(user);
        return ResponseEntity.ok(Map.of(
            "message", "User registered successfully",
            "username", user.username,
            "role", user.role.name()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        if (!jwt.isValid(token)) return ResponseEntity.status(401).build();
        return users.findByUsername(jwt.getUsername(token))
            .map(u -> ResponseEntity.ok(Map.of(
                "id", u.id, "username", u.username, "email", u.email,
                "role", u.role.name(), "fullName", u.fullName != null ? u.fullName : "",
                "tenantId", u.tenantId)))
            .orElse(ResponseEntity.status(404).build());
    }
}