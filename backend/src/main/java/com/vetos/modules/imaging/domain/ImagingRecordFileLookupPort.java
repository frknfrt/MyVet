package com.vetos.modules.imaging.domain;

import java.util.List;
import java.util.UUID;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29):
 * ai modulunun bir goruntuleme kaydina eklenmis dosyalarin icerigini
 * (OCR/vision cagrisi icin) okumasi icin TEK kapi -- ImagingRecordFileRepository'yi
 * ASLA baska modul dogrudan import etmez.
 */
public interface ImagingRecordFileLookupPort {

    /**
     * fileIds'in her biri imagingRecordId'ye ait olmali, aksi halde
     * ImagingRecordNotFoundException firlar. Donen liste fileIds ile ayni sirada olur.
     */
    List<ImagingRecordFileForOcr> findFiles(UUID imagingRecordId, List<UUID> fileIds);
}
