package io.sentinel.backend.security;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "users")
public class UserEntity {
    @Id public String id;
    @Column(unique=true, nullable=false) public String username;
    @Column(unique=true, nullable=false) public String email;
    @Column(name="password_hash", nullable=false) public String passwordHash;
    public String fullName;
    @Enumerated(EnumType.STRING) public Role role = Role.REVIEWER;
    public String tenantId = "default";
    public boolean active = true;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
}