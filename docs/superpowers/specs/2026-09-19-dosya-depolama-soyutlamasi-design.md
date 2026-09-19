# Dosya/Medya Depolama Soyutlaması — Tasarım Dokümanı

**Tarih:** 2026-09-19
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `platform/storage` (yeni), `modules/imaging`, `modules/lab`

## 1. Bağlam ve Amaç

Röntgen görselleri (`imaging`) ve lab sonucu dosyaları (`lab`), tek dosya için 15MB'a kadar, doğrudan Postgres'te `byte[]` (`bytea`) kolonunda tutuluyor. İki gerçek sorun var:

1. **Ölçekte:** Yedekleme boyutu/süresi, binary içerik arttıkça sınırsız büyür; sağlayıcı bağımsız değil, Postgres'e kilitli.
2. **Şu an, gerçek bir performans sorunu:** `ImagingRecordFile`/`LabResultFile` entity'lerinde `content` alanı `@Basic` (varsayılan **eager**) — `findByImagingRecordId(...)` gibi sadece **dosya listesi** göstermek için çağrılan sorgular bile her dosyanın **tüm binary içeriğini** veritabanından çekip JVM belleğine alıyor, sonra atıyor (API yanıtı sadece dosya adı/boyutu döndürüyor, içerik hiç kullanılmıyor). `@Basic(fetch = LAZY)` `byte[]` için Hibernate'de bytecode enhancement olmadan güvenilir çalışmıyor — asıl çözüm içeriği ayrı bir tabloya taşımak (bkz. §3).

**Kullanıcıyla netleşen kapsam kararı:** Hedef nesne depolama sağlayıcısı (S3/R2/B2/MinIO) **henüz belli değil** — B'deki deploy platformu kararıyla aynı durum. Bu tur, hangi sağlayıcı seçilirse seçilsin değişmeyecek kısmı kapsıyor: bir `FileStoragePort` soyutlaması + bugünkü Postgres davranışını onun arkasına taşımak. Gerçek S3 taşınması, sağlayıcı netleşince ayrı, çok daha küçük bir tur.

## 2. Kapsam

**Bu turda yapılacak:**
- `platform/storage/FileStoragePort.java` — sağlayıcıdan bağımsız depolama arayüzü.
- `platform/storage/PostgresFileStorageAdapter.java` — bugünkü davranışı (Postgres'te bytea) bu arayüzün arkasında sürdüren varsayılan implementasyon.
- `ImagingRecordFile`/`LabResultFile`'dan `content` kolonunun kaldırılıp yerine `storage_ref` (opak referans) eklenmesi — bu, §1'deki eager-loading sorununu da **yapısal olarak** çözüyor (içerik artık o entity'nin kolonu bile değil, yanlışlıkla çekilemez).
- Mevcut verinin yeni `stored_files` tablosuna taşınması (migration).

**Kapsam dışı (bilinçli olarak):**
- **Gerçek S3/R2/B2 adaptörü** — sağlayıcı netleşmeden yazılamaz. `FileStoragePort`'un yeni bir implementasyonu olarak eklenecek, mevcut hiçbir çağıran koda dokunmadan (Open/Closed — `docs/architecture.md` §3 ile aynı desen).
- **Presigned URL / doğrudan istemci-sağlayıcı indirme** — gerçek bir bulut sağlayıcısı olmadan anlamsız; ileride `FileStoragePort`'a eklenecek ayrı bir metot (`generateDownloadUrl`), mevcut `retrieve()`'i bozmadan. Şu anki tasarım (backend üzerinden akışla indirme) Postgres'te zaten tek makul yol, S3'e geçtiğimizde de çalışmaya devam eder — sadece optimal olmaz.
- **Dosya silme** — ne `imaging` ne `lab` modülünde şu an bir "dosya sil" özelliği var; `FileStoragePort`'a kullanılmayan bir `delete()` metodu eklenmiyor (YAGNI, bkz. proje genelinde tekrarlanan ilke).

## 3. `FileStoragePort` ve Postgres Adaptörü

```java
// platform/storage/FileStoragePort.java
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

```java
// platform/storage/StoredFile.java — yeni, paylasilan tek bir icerik tablosu
@Entity
@Table(name = "stored_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {
    @Id
    private UUID id; // DIKKAT: GeneratedValue degil -- store() cagirani kendi id'sini secer, bkz. asagida

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

```java
// platform/storage/PostgresFileStorageAdapter.java
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

**`StoredFile`'ın kendi `tenant_id`'si yok, bilinçli olarak:** Bu tabloya erişimin tek yolu, zaten kiracı kontrolü yapılmış bir üst kayıttan (`ImagingRecordFile`/`LabResultFile`, Kiracı İzolasyonu dokümanında `@TenantId` kapsamına giriyor) gelen bir `storageRef` — saldırgan `storageRef`'i doğrudan tahmin edip çağıramaz, çünkü hiçbir API bu değeri doğrudan almıyor, hep metadata satırı üzerinden dolaylı erişiliyor (`NotificationLog`'un iki erişim noktası mantığıyla aynı gerekçe — Arka Plan İş Güvenilirliği dokümanı §6).

## 4. Entity ve Migration Değişiklikleri

`ImagingRecordFile`/`LabResultFile`: `content: byte[]` kaldırılır, `storageRef: String` eklenir. `create(...)` factory metodu artık `content` yerine `storageRef` + `fileSize` (upload sırasında `content.length`'ten, use-case seviyesinde hesaplanır) alır.

Migration (sıradaki V-numarasından, implementasyon anında atanır):
```sql
CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    content BYTEA NOT NULL,
    content_type VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ayni id'yi tasi -- her dosya satiri tam olarak bir stored_files satirina
-- karsilik gelir, id eslestirmesi icin ayri bir arama/join gerekmez.
INSERT INTO stored_files (id, content, content_type, created_at)
SELECT id, content, content_type, uploaded_at FROM imaging_record_files;
INSERT INTO stored_files (id, content, content_type, created_at)
SELECT id, content, content_type, uploaded_at FROM lab_result_files;

ALTER TABLE imaging_record_files ADD COLUMN storage_ref UUID;
UPDATE imaging_record_files SET storage_ref = id;
ALTER TABLE imaging_record_files ALTER COLUMN storage_ref SET NOT NULL;
ALTER TABLE imaging_record_files DROP COLUMN content;

ALTER TABLE lab_result_files ADD COLUMN storage_ref UUID;
UPDATE lab_result_files SET storage_ref = id;
ALTER TABLE lab_result_files ALTER COLUMN storage_ref SET NOT NULL;
ALTER TABLE lab_result_files DROP COLUMN content;
```

Upload use-case değişikliği (her iki modülde aynı şekil):
```java
// UploadImagingRecordFileUseCase
@Transactional
public UUID execute(UUID imagingRecordId, String fileName, String contentType, byte[] content) {
    String storageRef = fileStoragePort.store(content, contentType);
    ImagingRecordFile file = ImagingRecordFile.create(imagingRecordId, fileName, contentType, content.length, storageRef);
    return imagingRecordFileRepository.save(file).getId();
}
```

İndirme controller'ı değişikliği (her iki modülde aynı şekil):
```java
// önce: .body(new ByteArrayResource(file.content()))
.body(new ByteArrayResource(fileStoragePort.retrieve(file.getStorageRef())))
```

## 5. Test Stratejisi

- `PostgresFileStorageAdapter` — mock repository ile `store`/`retrieve` birim testi; bilinmeyen bir `storageRef` için `StoredFileNotFoundException`.
- `UploadImagingRecordFileUseCase`/`UploadLabResultFileUseCase` — mock `FileStoragePort` ile, `store()`'un doğru içerik/content-type ile çağrıldığı ve dönen referansın entity'ye doğru yazıldığı.
- Regresyon: mevcut imaging/lab e2e testleri (varsa) hâlâ geçmeli — upload/download akışının dışarıdan davranışı değişmiyor, sadece içeride nasıl saklandığı değişiyor.
- `ApplicationModulesTest` — yeni `platform/storage` paketinin modül sınırlarını bozmadığının doğrulanması.

## 6. Açık Sorular

Yok — tasarım kullanıcı onayından geçti. Sağlayıcı netleştiğinde §2'deki "kapsam dışı" maddeleri (gerçek adaptör, presigned URL) ayrı, çok daha küçük bir tasarım turu olacak.
