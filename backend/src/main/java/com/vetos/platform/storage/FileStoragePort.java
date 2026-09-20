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
