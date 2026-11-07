package com.vetos.modules.dataimport.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Platform admin varligi: bilerek @TenantId YOK. */
@Entity
@Table(name = "import_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportBatchStatus status;

    @Column(name = "source_file")
    private byte[] sourceFile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private ImportReport report;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rejected_match_source_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> rejectedMatchSourceIds = new ArrayList<>();

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_by_admin_id", nullable = false, updatable = false)
    private UUID createdByAdminId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    public static ImportBatch preview(UUID tenantId, String sourceSystem, String fileName, byte[] file,
                                      ImportReport report, int totalRows, UUID adminId) {
        ImportBatch b = new ImportBatch();
        b.tenantId = tenantId;
        b.sourceSystem = sourceSystem;
        b.fileName = fileName;
        b.sourceFile = file;
        b.report = report;
        b.totalRows = totalRows;
        b.createdByAdminId = adminId;
        b.status = ImportBatchStatus.PREVIEWED;
        b.createdAt = Instant.now();
        return b;
    }

    public void startCommit(List<String> rejectedMatchSourceIds) {
        require(ImportBatchStatus.PREVIEWED, ImportBatchStatus.COMMITTING);
        this.rejectedMatchSourceIds = rejectedMatchSourceIds == null
            ? new ArrayList<>() : new ArrayList<>(rejectedMatchSourceIds);
        this.processedRows = 0;
        this.status = ImportBatchStatus.COMMITTING;
    }

    public void progress(int processedRows) {
        this.processedRows = processedRows;
    }

    /** Isci dosyayi yeniden dogrular; admin gercekte ne oldugunu bu rapordan gorur. */
    public void refreshReport(ImportReport report) {
        requireStatus(ImportBatchStatus.COMMITTING, "rapor güncelleme");
        this.report = report;
    }

    public void markCommitted() {
        require(ImportBatchStatus.COMMITTING, ImportBatchStatus.COMMITTED);
        this.status = ImportBatchStatus.COMMITTED;
        this.committedAt = Instant.now();
        this.sourceFile = null;
    }

    public void markFailed(String message) {
        require(ImportBatchStatus.COMMITTING, ImportBatchStatus.FAILED);
        this.status = ImportBatchStatus.FAILED;
        this.errorMessage = message;
        this.sourceFile = null;
        scrubReport();
    }

    /** COMMITTED ya da (otomatik temizligi basarisiz olmus) FAILED aktarim geri alinabilir. */
    public void markRolledBack() {
        if (status != ImportBatchStatus.COMMITTED && status != ImportBatchStatus.FAILED) {
            throw new IllegalStateException(
                "Aktarım " + status + " durumundan " + ImportBatchStatus.ROLLED_BACK + " durumuna geçirilemez");
        }
        this.status = ImportBatchStatus.ROLLED_BACK;
        this.rolledBackAt = Instant.now();
        scrubReport();
    }

    public void discard() {
        require(ImportBatchStatus.PREVIEWED, ImportBatchStatus.DISCARDED);
        this.status = ImportBatchStatus.DISCARDED;
        this.sourceFile = null;
        scrubReport();
    }

    /**
     * KVKK: veri klinige gecmeyen (iptal, basarisiz) ya da geri alinan aktarimin raporunda kisisel veri
     * (satir hatalari/uyarilari, sahip eslesmeleri) tutulmaz. COMMITTED tam raporu saklar: admin neyin aktarildigini gorur.
     */
    private void scrubReport() {
        if (report != null) {
            this.report = report.withoutPersonalData();
        }
    }

    private void requireStatus(ImportBatchStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException("Aktarım " + status + " durumundayken " + operation + " yapılamaz");
        }
    }

    private void require(ImportBatchStatus expected, ImportBatchStatus target) {
        if (status != expected) {
            throw new IllegalStateException("Aktarım " + status + " durumundan " + target + " durumuna geçirilemez");
        }
    }
}
