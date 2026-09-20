package com.vetos.modules.lab.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_result_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabResultFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "lab_result_id", nullable = false)
    private UUID labResultId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_ref", nullable = false)
    private String storageRef;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public static LabResultFile create(
        UUID tenantId, UUID labResultId, String fileName, String contentType, long fileSize, String storageRef
    ) {
        LabResultFile file = new LabResultFile();
        file.tenantId = tenantId;
        file.labResultId = labResultId;
        file.fileName = fileName;
        file.contentType = contentType;
        file.fileSize = fileSize;
        file.storageRef = storageRef;
        file.uploadedAt = Instant.now();
        return file;
    }
}
