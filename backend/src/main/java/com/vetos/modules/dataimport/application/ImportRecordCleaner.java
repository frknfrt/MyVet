package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportRecordDeleter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bir partinin olusturdugu kayitlari tek transaction'da, partinin kiracisinda siler:
 * once hastalar, sonra sahipler (yalnizca created_by_import=true olanlar), en son partinin TUM baglantilari.
 * Eslesen mevcut sahiplere ve turlere dokunulmaz. Bagimlilik kontrolu YAPMAZ (Task 6 genel geri almada ekler);
 * isci hata yolunda dogrudan kullanir.
 */
@Component
@RequiredArgsConstructor
public class ImportRecordCleaner {

    private final ImportRecordDeleter deleter;
    private final ImportTransactions transactions;

    public Result deleteCreatedRecords(ImportBatch batch) {
        return transactions.asTenant(batch.getTenantId(), () -> new Result(
            deleter.deleteCreatedPatients(batch.getTenantId(), batch.getId()),
            deleter.deleteCreatedOwners(batch.getTenantId(), batch.getId()),
            deleter.deleteLinks(batch.getTenantId(), batch.getId())));
    }

    public record Result(int patientsDeleted, int ownersDeleted, int linksDeleted) {
    }
}
