package com.vetos.modules.lab.domain;

import java.util.List;
import java.util.UUID;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29):
 * ai modulunun bir lab sonucuna eklenmis dosyalarin icerigini (OCR/vision
 * cagrisi icin) okumasi icin TEK kapi (@docs/reference-module.md Bolum 12) --
 * LabResultFileRepository'yi ASLA baska modul dogrudan import etmez.
 */
public interface LabResultFileLookupPort {

    /**
     * fileIds'in her biri labResultId'ye ait olmali, aksi halde (bulunamadi ya
     * da baska bir sonuca ait) LabResultNotFoundException firlar. Donen liste
     * fileIds ile ayni sirada olur.
     */
    List<LabResultFileForOcr> findFiles(UUID labResultId, List<UUID> fileIds);
}
