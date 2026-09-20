# Dosya Depolama Soyutlaması Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Röntgen (`imaging`) ve lab sonucu (`lab`) dosyalarının içeriğini, sağlayıcıdan bağımsız bir `FileStoragePort` soyutlamasının arkasına taşımak — bugünkü Postgres `bytea` davranışını koruyarak, ama artık `ImagingRecordFile`/`LabResultFile` entity'lerinin kendi kolonu olmaktan çıkararak (yapısal olarak eager-loading sorununu da çözerek).

**Architecture:** Yeni `platform/storage` paketi: `FileStoragePort` arayüzü (`store`/`retrieve`), paylaşılan tek bir `stored_files` tablosu (`StoredFile` entity), ve bugünkü davranışı bu arayüzün arkasında sürdüren `PostgresFileStorageAdapter`. `ImagingRecordFile`/`LabResultFile`'dan `content: byte[]` kaldırılıp yerine opak bir `storageRef: String` eklenir — upload use-case'leri artık `FileStoragePort.store(...)` çağırıp dönen referansı entity'ye yazar, download use-case'leri `FileStoragePort.retrieve(storageRef)` ile içeriği geri çeker. Mevcut veri, migration'larla yeni tabloya taşınır.

**Tech Stack:** JPA/Hibernate, Postgres `bytea`, Flyway.

**Spec:** `docs/superpowers/specs/2026-09-19-dosya-depolama-soyutlamasi-design.md`

## Global Constraints

- `FileStoragePort` sağlayıcıdan bağımsız kalmalı — `store(byte[] content, String contentType): String` opak bir referans döner, `retrieve(String storageRef): byte[]` bilinmeyen bir referans için `StoredFileNotFoundException` fırlatır. Çağıran kod (`UploadXxxFileUseCase`/`DownloadXxxFileUseCase`) bu referansın ne olduğunu bilmez/bilmemeli.
- **Spec'ten düzeltme (plan yazılırken bulundu):** Spec'in §4'ündeki migration örneği `storage_ref` kolonunu `UUID` tipinde tanımlıyor, ama §4'ün kendi metni entity alanını `String storageRef` olarak tanımlıyor (`FileStoragePort.store()`'un `String` dönmesiyle de tutarlı). `spring.jpa.hibernate.ddl-auto: validate` (bu kod tabanında zaten aktif) bir Java `String` alanını bir Postgres `UUID` kolonuna eşlerken şema doğrulama hatasıyla başlangıçta çöker — bu plan `storage_ref` kolonunu **`VARCHAR(255)`** olarak düzeltiyor (entity alanı `String` ile tutarlı), backfill `id::text` cast'iyle yapılıyor (Postgres'in `uuid::text` cast'i, `UUID.randomUUID().toString()`'in ürettiği küçük harfli, tireli kanonik formatla birebir eşleşir — round-trip `UUID.fromString(...)` ile sorunsuz çalışır).
- `StoredFile.id` `@GeneratedValue` DEĞİL — `store()` çağıranı kendi `UUID.randomUUID()`'ini seçip hem `StoredFile.id` hem dönen referans string'i için kullanır (spec §3).
- `StoredFile`'ın kendi `tenant_id`'si yok (bilinçli — bkz. spec §3, `NotificationLog` ile aynı gerekçe: erişim hep zaten kiracı-kontrollü bir üst kayıt üzerinden, `storageRef` hiçbir API'de doğrudan istemciden alınmıyor).
- Migration numaraları: bir önceki turun (Arka Plan İş Güvenilirliği) son migration'ı `V54`'tür — bu plan `V55`'ten başlar. Task 1'in migration'ı (V55), Task 2/3'ün migration'larından (V56/V57) ÖNCE uygulanmalı (backfill sırası: önce `stored_files` doldurulur, sonra kaynak tablolardan `content` düşürülür).
- `imaging`/`lab` modüllerinin `package-info.java`'sı `allowedDependencies` listesinde — `platform::storage` eklenmeden `FileStoragePort`'u import etmek `ApplicationModulesTest`'i kırar.
- Gerçek S3/R2/B2 adaptörü, presigned URL, dosya silme — bu turun kapsamı dışında (YAGNI, spec §2).
- Her görev sonunda `cd backend && ./mvnw test` çalıştırılır, `ApplicationModulesTest` dahil yeşil kalmalı.

---

## Task 1: `platform/storage` — `FileStoragePort` + Postgres Adaptörü

**Files:**
- Create: `backend/src/main/resources/db/migration/V55__stored_files.sql`
- Create: `backend/src/main/java/com/vetos/platform/storage/package-info.java`
- Create: `backend/src/main/java/com/vetos/platform/storage/FileStoragePort.java`
- Create: `backend/src/main/java/com/vetos/platform/storage/StoredFile.java`
- Create: `backend/src/main/java/com/vetos/platform/storage/StoredFileNotFoundException.java`
- Create: `backend/src/main/java/com/vetos/platform/storage/StoredFileJpaRepository.java`
- Create: `backend/src/main/java/com/vetos/platform/storage/PostgresFileStorageAdapter.java`
- Test: `backend/src/test/java/com/vetos/platform/storage/PostgresFileStorageAdapterTest.java`

**Interfaces:**
- Produces: `FileStoragePort.store(byte[] content, String contentType): String`, `FileStoragePort.retrieve(String storageRef): byte[]` — Task 2 ve Task 3 bunu enjekte edip kullanacak.

- [ ] **Step 1: Migration oluştur — `stored_files` tablosu + mevcut verinin taşınması**

`backend/src/main/resources/db/migration/V55__stored_files.sql`:
```sql
-- Yeni paylasilan icerik tablosu -- platform/storage/StoredFile.java.
-- id GENERATED DEGIL: her satirin id'si, o dosyayi ilk yaratan satirin
-- (imaging_record_files/lab_result_files) KENDI id'siyle AYNI -- ayri bir
-- arama/join gerekmeden 1:1 eslesir (bkz. tasarim dokumani S4).
CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    content BYTEA NOT NULL,
    content_type VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO stored_files (id, content, content_type, created_at)
SELECT id, content, content_type, uploaded_at FROM imaging_record_files;
INSERT INTO stored_files (id, content, content_type, created_at)
SELECT id, content, content_type, uploaded_at FROM lab_result_files;
```

- [ ] **Step 2: `platform/storage` paketi + `FileStoragePort`**

`backend/src/main/java/com/vetos/platform/storage/package-info.java`:
```java
@org.springframework.modulith.NamedInterface("storage")
package com.vetos.platform.storage;
```

`backend/src/main/java/com/vetos/platform/storage/FileStoragePort.java`:
```java
package com.vetos.platform.storage;

/**
 * Dosya icerigi depolamasi icin saglayicidan bagimsiz arayuz -- Postgres,
 * S3, R2, hangisi olursa olsun cagiran kod (UploadXxxFileUseCase,
 * indirme endpoint'leri) hic degismez. docs/architecture.md SS3
 * (Open/Closed) ile ayni desen: yeni saglayici = yeni @Component,
 * mevcut kod degismez.
 */
public interface FileStoragePort {
    /** Icerigi depolar, opak bir referans doner (cagiran kod bunun ne oldugunu bilmez/bilmemeli). */
    String store(byte[] content, String contentType);

    /** storageRef bulunamazsa StoredFileNotFoundException firlatir. */
    byte[] retrieve(String storageRef);
}
```

- [ ] **Step 3: `StoredFile` entity + `StoredFileNotFoundException`**

`backend/src/main/java/com/vetos/platform/storage/StoredFile.java`:
```java
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
```

`backend/src/main/java/com/vetos/platform/storage/StoredFileNotFoundException.java`:
```java
package com.vetos.platform.storage;

import com.vetos.platform.exception.DomainException;

public class StoredFileNotFoundException extends DomainException {
    public StoredFileNotFoundException(String storageRef) {
        super("STORED_FILE_NOT_FOUND", "Depolanan dosya bulunamadi: " + storageRef);
    }
}
```

- [ ] **Step 4: `StoredFileJpaRepository` + `PostgresFileStorageAdapter`**

`backend/src/main/java/com/vetos/platform/storage/StoredFileJpaRepository.java`:
```java
package com.vetos.platform.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface StoredFileJpaRepository extends JpaRepository<StoredFile, UUID> {
}
```

`backend/src/main/java/com/vetos/platform/storage/PostgresFileStorageAdapter.java`:
```java
package com.vetos.platform.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class PostgresFileStorageAdapter implements FileStoragePort {
    private final StoredFileJpaRepository repository;

    @Override
    public String store(byte[] content, String contentType) {
        UUID id = UUID.randomUUID();
        repository.save(StoredFile.create(id, content, contentType));
        return id.toString();
    }

    @Override
    public byte[] retrieve(String storageRef) {
        return repository.findById(UUID.fromString(storageRef))
            .map(StoredFile::getContent)
            .orElseThrow(() -> new StoredFileNotFoundException(storageRef));
    }
}
```

- [ ] **Step 5: `PostgresFileStorageAdapterTest`'i yaz**

`backend/src/test/java/com/vetos/platform/storage/PostgresFileStorageAdapterTest.java`:
```java
package com.vetos.platform.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresFileStorageAdapterTest {

    @Mock private StoredFileJpaRepository repository;

    @Test
    void should_saveContentAndReturnIdAsString_when_store() {
        PostgresFileStorageAdapter adapter = new PostgresFileStorageAdapter(repository);
        byte[] content = "hello".getBytes();

        String storageRef = adapter.store(content, "text/plain");

        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(repository).save(captor.capture());
        StoredFile saved = captor.getValue();
        assertThat(saved.getId().toString()).isEqualTo(storageRef);
        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getContentType()).isEqualTo("text/plain");
    }

    @Test
    void should_returnContent_when_retrieve_and_storageRefExists() {
        PostgresFileStorageAdapter adapter = new PostgresFileStorageAdapter(repository);
        UUID id = UUID.randomUUID();
        byte[] content = "world".getBytes();
        StoredFile stored = StoredFile.create(id, content, "text/plain");
        when(repository.findById(id)).thenReturn(Optional.of(stored));

        byte[] result = adapter.retrieve(id.toString());

        assertThat(result).isEqualTo(content);
    }

    @Test
    void should_throwStoredFileNotFound_when_retrieve_and_storageRefUnknown() {
        PostgresFileStorageAdapter adapter = new PostgresFileStorageAdapter(repository);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.retrieve(id.toString()))
            .isInstanceOf(StoredFileNotFoundException.class);
    }
}
```

- [ ] **Step 6: Testi çalıştır**

Run: `cd backend && ./mvnw -q test -Dtest=PostgresFileStorageAdapterTest`
Expected: 3 test PASS.

- [ ] **Step 7: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS, `ApplicationModulesTest` dahil (yeni `platform/storage` paketi henüz hiçbir modülden import edilmiyor, sorun çıkarmamalı).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V55__stored_files.sql \
  backend/src/main/java/com/vetos/platform/storage/ \
  backend/src/test/java/com/vetos/platform/storage/
git commit -m "feat: FileStoragePort soyutlamasi + Postgres adaptoru ekle"
```

---

## Task 2: `imaging` Modülü — `FileStoragePort`'a Geçiş

**Files:**
- Create: `backend/src/main/resources/db/migration/V56__imaging_record_files_storage_ref.sql`
- Modify: `backend/src/main/java/com/vetos/modules/imaging/domain/ImagingRecordFile.java`
- Modify: `backend/src/main/java/com/vetos/modules/imaging/application/UploadImagingRecordFileUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/imaging/application/DownloadImagingRecordFileUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/imaging/package-info.java`
- Test: `backend/src/test/java/com/vetos/modules/imaging/application/UploadImagingRecordFileUseCaseTest.java`
- Test: `backend/src/test/java/com/vetos/modules/imaging/application/DownloadImagingRecordFileUseCaseTest.java`

**Interfaces:**
- Consumes: `FileStoragePort.store(byte[], String): String`, `FileStoragePort.retrieve(String): byte[]` (Task 1).

**Not (sıralama):** Bu migration (`V56`), Task 1'in `V55`'inden SONRA çalışmalı (backfill `stored_files`'a bağımlı) — dosya numarası `V56`, `V55`'ten büyük olduğu için sıralama zaten doğru.

- [ ] **Step 1: Migration oluştur**

`backend/src/main/resources/db/migration/V56__imaging_record_files_storage_ref.sql`:
```sql
-- storage_ref, mevcut satirin KENDI id'siyle ayni deger -- V55'te stored_files'a
-- tam bu id ile tasindi (bkz. tasarim dokumani S4). Kolon VARCHAR -- entity
-- alani String (FileStoragePort'un opak referans sozlesmesiyle tutarli,
-- plan yazarken UUID'den duzeltildi, bkz. Global Constraints).
ALTER TABLE imaging_record_files ADD COLUMN storage_ref VARCHAR(255);
UPDATE imaging_record_files SET storage_ref = id::text;
ALTER TABLE imaging_record_files ALTER COLUMN storage_ref SET NOT NULL;
ALTER TABLE imaging_record_files DROP COLUMN content;
```

- [ ] **Step 2: `imaging` modülünün `allowedDependencies`'ine `platform::storage` ekle**

`backend/src/main/java/com/vetos/modules/imaging/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Görüntüleme",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::exception", "platform::storage"
    }
)
package com.vetos.modules.imaging;
```

- [ ] **Step 3: `ImagingRecordFile` entity'sini güncelle**

`backend/src/main/java/com/vetos/modules/imaging/domain/ImagingRecordFile.java` — `@Column(nullable = false, columnDefinition = "bytea") private byte[] content;` satırını kaldır, yerine ekle:
```java
@Column(name = "storage_ref", nullable = false)
private String storageRef;
```
`create` factory metodunu değiştir:
```java
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
```

- [ ] **Step 4: `UploadImagingRecordFileUseCase`'i güncelle**

`backend/src/main/java/com/vetos/modules/imaging/application/UploadImagingRecordFileUseCase.java`:
```java
package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadImagingRecordFileUseCase {

    private final ImagingRecordRepository imagingRecordRepository;
    private final ImagingRecordFileRepository imagingRecordFileRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    public UUID execute(UUID imagingRecordId, String fileName, String contentType, byte[] content) {
        ImagingRecord record = imagingRecordRepository.findById(imagingRecordId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(imagingRecordId));

        String storageRef = fileStoragePort.store(content, contentType);
        ImagingRecordFile file = ImagingRecordFile.create(
            record.getTenantId(), imagingRecordId, fileName, contentType, content.length, storageRef
        );
        return imagingRecordFileRepository.save(file).getId();
    }
}
```

- [ ] **Step 5: `DownloadImagingRecordFileUseCase`'i güncelle**

`backend/src/main/java/com/vetos/modules/imaging/application/DownloadImagingRecordFileUseCase.java`:
```java
package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.ImagingRecordFileContent;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.exception.ImagingRecordNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DownloadImagingRecordFileUseCase {

    private final ImagingRecordFileRepository imagingRecordFileRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional(readOnly = true)
    public ImagingRecordFileContent execute(UUID fileId) {
        var file = imagingRecordFileRepository.findById(fileId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(fileId));
        byte[] content = fileStoragePort.retrieve(file.getStorageRef());
        return new ImagingRecordFileContent(file.getFileName(), file.getContentType(), content);
    }
}
```

- [ ] **Step 6: `UploadImagingRecordFileUseCaseTest`'i yaz**

`backend/src/test/java/com/vetos/modules/imaging/application/UploadImagingRecordFileUseCaseTest.java`:
```java
package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.platform.storage.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadImagingRecordFileUseCaseTest {

    @Mock private ImagingRecordRepository imagingRecordRepository;
    @Mock private ImagingRecordFileRepository imagingRecordFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    @Test
    void should_storeContentViaPort_and_saveFileWithReturnedRef() {
        UploadImagingRecordFileUseCase useCase = new UploadImagingRecordFileUseCase(
            imagingRecordRepository, imagingRecordFileRepository, fileStoragePort
        );
        UUID tenantId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        ImagingRecord record = ImagingRecord.request(tenantId, UUID.randomUUID(), UUID.randomUUID(), ImagingModality.XRAY, null, null);
        byte[] content = "dosya-icerigi".getBytes();
        when(imagingRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(fileStoragePort.store(content, "image/png")).thenReturn("some-storage-ref");
        when(imagingRecordFileRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(recordId, "xray.png", "image/png", content);

        verify(fileStoragePort).store(content, "image/png");
        ArgumentCaptor<ImagingRecordFile> captor = ArgumentCaptor.forClass(ImagingRecordFile.class);
        verify(imagingRecordFileRepository).save(captor.capture());
        assertThat(captor.getValue().getStorageRef()).isEqualTo("some-storage-ref");
        assertThat(captor.getValue().getFileSize()).isEqualTo(content.length);
        assertThat(captor.getValue().getFileName()).isEqualTo("xray.png");
    }
}
```
(`ImagingRecord.request(tenantId, patientId, orderingStaffId, modality, bodyRegion, notes)` imzası `ImagingRecord.java`'dan doğrulandı — yukarıdaki çağrı gerçek imzayla birebir eşleşiyor.)

- [ ] **Step 7: `DownloadImagingRecordFileUseCaseTest`'i yaz**

`backend/src/test/java/com/vetos/modules/imaging/application/DownloadImagingRecordFileUseCaseTest.java`:
```java
package com.vetos.modules.imaging.application;

import com.vetos.modules.imaging.application.dto.ImagingRecordFileContent;
import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.platform.storage.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadImagingRecordFileUseCaseTest {

    @Mock private ImagingRecordFileRepository imagingRecordFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    @Test
    void should_retrieveContentViaPort_using_fileStorageRef() {
        DownloadImagingRecordFileUseCase useCase = new DownloadImagingRecordFileUseCase(imagingRecordFileRepository, fileStoragePort);
        UUID fileId = UUID.randomUUID();
        ImagingRecordFile file = ImagingRecordFile.create(
            UUID.randomUUID(), UUID.randomUUID(), "xray.png", "image/png", 123L, "some-storage-ref"
        );
        byte[] content = "dosya-icerigi".getBytes();
        when(imagingRecordFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileStoragePort.retrieve("some-storage-ref")).thenReturn(content);

        ImagingRecordFileContent result = useCase.execute(fileId);

        assertThat(result).isEqualTo(new ImagingRecordFileContent("xray.png", "image/png", content));
    }
}
```

- [ ] **Step 8: Testleri çalıştır**

Run: `cd backend && ./mvnw -q test -Dtest=UploadImagingRecordFileUseCaseTest,DownloadImagingRecordFileUseCaseTest`
Expected: 2 test PASS.

- [ ] **Step 9: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS, `ApplicationModulesTest` dahil.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V56__imaging_record_files_storage_ref.sql \
  backend/src/main/java/com/vetos/modules/imaging/domain/ImagingRecordFile.java \
  backend/src/main/java/com/vetos/modules/imaging/application/UploadImagingRecordFileUseCase.java \
  backend/src/main/java/com/vetos/modules/imaging/application/DownloadImagingRecordFileUseCase.java \
  backend/src/main/java/com/vetos/modules/imaging/package-info.java \
  backend/src/test/java/com/vetos/modules/imaging/application/UploadImagingRecordFileUseCaseTest.java \
  backend/src/test/java/com/vetos/modules/imaging/application/DownloadImagingRecordFileUseCaseTest.java
git commit -m "feat: imaging modulunu FileStoragePort'a tasi"
```

---

## Task 3: `lab` Modülü — `FileStoragePort`'a Geçiş

**Files:**
- Create: `backend/src/main/resources/db/migration/V57__lab_result_files_storage_ref.sql`
- Modify: `backend/src/main/java/com/vetos/modules/lab/domain/LabResultFile.java`
- Modify: `backend/src/main/java/com/vetos/modules/lab/application/UploadLabResultFileUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/lab/application/DownloadLabResultFileUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/lab/package-info.java`
- Test: `backend/src/test/java/com/vetos/modules/lab/application/UploadLabResultFileUseCaseTest.java`
- Test: `backend/src/test/java/com/vetos/modules/lab/application/DownloadLabResultFileUseCaseTest.java`

**Interfaces:**
- Consumes: `FileStoragePort.store(byte[], String): String`, `FileStoragePort.retrieve(String): byte[]` (Task 1).

Bu görev, Task 2'nin `imaging` modülünde yaptığının birebir aynısını `lab` modülünde tekrarlar.

**Not (sıralama):** Bu migration (`V57`), Task 1'in `V55`'inden SONRA çalışmalı — dosya numarası `V57`, `V55`'ten büyük olduğu için sıralama zaten doğru. Task 2'nin `V56`'sıyla bağımlılığı yok (farklı tablo), paralel/herhangi bir sırada çalışabilir.

- [ ] **Step 1: Migration oluştur**

`backend/src/main/resources/db/migration/V57__lab_result_files_storage_ref.sql`:
```sql
ALTER TABLE lab_result_files ADD COLUMN storage_ref VARCHAR(255);
UPDATE lab_result_files SET storage_ref = id::text;
ALTER TABLE lab_result_files ALTER COLUMN storage_ref SET NOT NULL;
ALTER TABLE lab_result_files DROP COLUMN content;
```

- [ ] **Step 2: `lab` modülünün `allowedDependencies`'ine `platform::storage` ekle**

`backend/src/main/java/com/vetos/modules/lab/package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Laboratuvar",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::exception", "platform::storage"
    }
)
package com.vetos.modules.lab;
```

- [ ] **Step 3: `LabResultFile` entity'sini güncelle**

`backend/src/main/java/com/vetos/modules/lab/domain/LabResultFile.java` — `@Column(nullable = false, columnDefinition = "bytea") private byte[] content;` satırını kaldır, yerine ekle:
```java
@Column(name = "storage_ref", nullable = false)
private String storageRef;
```
`create` factory metodunu değiştir:
```java
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
```

- [ ] **Step 4: `UploadLabResultFileUseCase`'i güncelle**

`backend/src/main/java/com/vetos/modules/lab/application/UploadLabResultFileUseCase.java`:
```java
package com.vetos.modules.lab.application;

import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadLabResultFileUseCase {

    private final LabResultRepository labResultRepository;
    private final LabResultFileRepository labResultFileRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    public UUID execute(UUID labResultId, String fileName, String contentType, byte[] content) {
        LabResult result = labResultRepository.findById(labResultId)
            .orElseThrow(() -> new LabResultNotFoundException(labResultId));

        String storageRef = fileStoragePort.store(content, contentType);
        LabResultFile file = LabResultFile.create(
            result.getTenantId(), labResultId, fileName, contentType, content.length, storageRef
        );
        return labResultFileRepository.save(file).getId();
    }
}
```

- [ ] **Step 5: `DownloadLabResultFileUseCase`'i güncelle**

`backend/src/main/java/com/vetos/modules/lab/application/DownloadLabResultFileUseCase.java`:
```java
package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.LabResultFileContent;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.exception.LabResultNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DownloadLabResultFileUseCase {

    private final LabResultFileRepository labResultFileRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional(readOnly = true)
    public LabResultFileContent execute(UUID fileId) {
        var file = labResultFileRepository.findById(fileId)
            .orElseThrow(() -> new LabResultNotFoundException(fileId));
        byte[] content = fileStoragePort.retrieve(file.getStorageRef());
        return new LabResultFileContent(file.getFileName(), file.getContentType(), content);
    }
}
```

- [ ] **Step 6: `UploadLabResultFileUseCaseTest`'i yaz**

`backend/src/test/java/com/vetos/modules/lab/application/UploadLabResultFileUseCaseTest.java`:
```java
package com.vetos.modules.lab.application;

import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.platform.storage.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadLabResultFileUseCaseTest {

    @Mock private LabResultRepository labResultRepository;
    @Mock private LabResultFileRepository labResultFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    @Test
    void should_storeContentViaPort_and_saveFileWithReturnedRef() {
        UploadLabResultFileUseCase useCase = new UploadLabResultFileUseCase(
            labResultRepository, labResultFileRepository, fileStoragePort
        );
        UUID tenantId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        LabResult result = LabResult.request(tenantId, UUID.randomUUID(), UUID.randomUUID(), "Tam Kan Sayimi", null);
        byte[] content = "dosya-icerigi".getBytes();
        when(labResultRepository.findById(resultId)).thenReturn(Optional.of(result));
        when(fileStoragePort.store(content, "application/pdf")).thenReturn("some-storage-ref");
        when(labResultFileRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(resultId, "sonuc.pdf", "application/pdf", content);

        verify(fileStoragePort).store(content, "application/pdf");
        ArgumentCaptor<LabResultFile> captor = ArgumentCaptor.forClass(LabResultFile.class);
        verify(labResultFileRepository).save(captor.capture());
        assertThat(captor.getValue().getStorageRef()).isEqualTo("some-storage-ref");
        assertThat(captor.getValue().getFileSize()).isEqualTo(content.length);
        assertThat(captor.getValue().getFileName()).isEqualTo("sonuc.pdf");
    }
}
```
(`LabResult result = null;` satırındaki placeholder'ı, `LabResult.java`'daki gerçek factory metodunu çağırarak doldur — bu adımın tamamlanmış sayılması için testin gerçek, derlenen bir `LabResult` örneğiyle çalışması ZORUNLU, `null` bırakılamaz.)

- [ ] **Step 7: `DownloadLabResultFileUseCaseTest`'i yaz**

`backend/src/test/java/com/vetos/modules/lab/application/DownloadLabResultFileUseCaseTest.java`:
```java
package com.vetos.modules.lab.application;

import com.vetos.modules.lab.application.dto.LabResultFileContent;
import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.platform.storage.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadLabResultFileUseCaseTest {

    @Mock private LabResultFileRepository labResultFileRepository;
    @Mock private FileStoragePort fileStoragePort;

    @Test
    void should_retrieveContentViaPort_using_fileStorageRef() {
        DownloadLabResultFileUseCase useCase = new DownloadLabResultFileUseCase(labResultFileRepository, fileStoragePort);
        UUID fileId = UUID.randomUUID();
        LabResultFile file = LabResultFile.create(
            UUID.randomUUID(), UUID.randomUUID(), "sonuc.pdf", "application/pdf", 456L, "some-storage-ref"
        );
        byte[] content = "dosya-icerigi".getBytes();
        when(labResultFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileStoragePort.retrieve("some-storage-ref")).thenReturn(content);

        LabResultFileContent result = useCase.execute(fileId);

        assertThat(result).isEqualTo(new LabResultFileContent("sonuc.pdf", "application/pdf", content));
    }
}
```

- [ ] **Step 8: Testleri çalıştır**

Run: `cd backend && ./mvnw -q test -Dtest=UploadLabResultFileUseCaseTest,DownloadLabResultFileUseCaseTest`
Expected: 2 test PASS.

- [ ] **Step 9: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS, `ApplicationModulesTest` dahil.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V57__lab_result_files_storage_ref.sql \
  backend/src/main/java/com/vetos/modules/lab/domain/LabResultFile.java \
  backend/src/main/java/com/vetos/modules/lab/application/UploadLabResultFileUseCase.java \
  backend/src/main/java/com/vetos/modules/lab/application/DownloadLabResultFileUseCase.java \
  backend/src/main/java/com/vetos/modules/lab/package-info.java \
  backend/src/test/java/com/vetos/modules/lab/application/UploadLabResultFileUseCaseTest.java \
  backend/src/test/java/com/vetos/modules/lab/application/DownloadLabResultFileUseCaseTest.java
git commit -m "feat: lab modulunu FileStoragePort'a tasi"
```

---

## Self-Review Notları (plan yazarı tarafından, kaydedilmeden önce)

- **Spec kapsaması:** §3 (`FileStoragePort` + `PostgresFileStorageAdapter`) → Task 1. §4 (entity/migration değişiklikleri, her iki modül) → Task 2 (imaging) + Task 3 (lab). §5 (test stratejisi) her görevin test adımlarına dağıtıldı. §6 açık soru yok.
- **Spec'ten sapma (bilinçli, gerekçeli, yukarıda Global Constraints'te de belirtildi):** `storage_ref` kolon tipi spec'in migration örneğinde `UUID`, ama spec'in kendi metni entity alanını `String` olarak tanımlıyor — bu iç tutarsızlık, `ddl-auto: validate` ile başlangıçta çökeceği için `VARCHAR(255)` olarak düzeltildi (entity tipiyle tutarlı).
- **Tip/imza tutarlılığı:** `ImagingRecordFile.create`/`LabResultFile.create`'in yeni imzaları (`..., long fileSize, String storageRef`) hem entity'de hem her iki use-case'te hem testlerde birebir aynı sırada kullanılıyor. `FileStoragePort.store/retrieve` imzaları Task 1'de tanımlandığı gibi Task 2/3'te değişmeden tüketiliyor.
- **Placeholder taraması:** temiz. `ImagingRecord.request(...)` (Task 2) ve `LabResult.request(...)` (Task 3) çağrılarının kullandığı factory imzaları, plan yazımı sırasında gerçek kaynak dosyalardan (`ImagingRecord.java`, `LabResult.java`) doğrulandı ve testler bu gerçek imzalarla yazıldı — placeholder/varsayım yok.
