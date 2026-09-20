package com.vetos.modules.imaging.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imaging_record_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImagingRecordFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "imaging_record_id", nullable = false)
    private UUID imagingRecordId;

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

    public static ImagingRecordFile create(
        UUID tenantId, UUID imagingRecordId, String fileName, String contentType, long fileSize, String storageRef
    ) {
        ImagingRecordFile file = new ImagingRecordFile();
        file.tenantId = tenantId;
        file.imagingRecordId = imagingRecordId;
        file.fileName = fileName;
        file.contentType = contentType;
        file.fileSize = fileSize;
        file.storageRef = storageRef;
        file.uploadedAt = Instant.now();
        return file;
    }
}
