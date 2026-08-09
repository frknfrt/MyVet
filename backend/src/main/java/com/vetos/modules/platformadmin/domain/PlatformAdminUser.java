package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_admin_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformAdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PlatformAdminUser register(String email, String passwordHash, String fullName) {
        PlatformAdminUser user = new PlatformAdminUser();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.createdAt = Instant.now();
        return user;
    }
}
