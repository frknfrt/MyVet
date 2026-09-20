package com.vetos.platform.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {
    @Id
    private UUID id; // DIKKAT: GeneratedValue degil -- store() cagirani kendi id'sini secer

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static StoredFile create(UUID id, byte[] content, String contentType) {
        StoredFile f = new StoredFile();
        f.id = id;
        f.content = content;
        f.contentType = contentType;
        f.createdAt = Instant.now();
        return f;
    }
}
