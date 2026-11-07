package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.application.excel.ImportWorkbookParser;
import com.vetos.modules.dataimport.application.excel.ParsedWorkbook;
import com.vetos.modules.dataimport.application.excel.TurkishText;
import com.vetos.modules.dataimport.domain.ExistingDataLookup;
import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.modules.dataimport.domain.exception.ImportTenantNotFoundException;
import com.vetos.modules.dataimport.domain.exception.InvalidImportFileException;
import com.vetos.modules.dataimport.domain.exception.InvalidImportRequestException;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dosyayi okur, dogrular, klinik verisi YAZMADAN bir onizleme partisi kaydeder.
 * Transaction'siz: dogrulama okumalari kendi kisa oturumlarinda, tek yazma batchRepository.save.
 */
@Service
@RequiredArgsConstructor
public class CreateImportPreviewUseCase {

    static final int MAX_SOURCE_SYSTEM_LENGTH = 60;

    private final TenantLookupPort tenantLookupPort;
    private final ImportWorkbookParser parser;
    private final ImportValidator validator;
    private final ExistingDataLookup lookup;
    private final ImportBatchRepository batchRepository;

    public ImportBatch execute(UUID tenantId, String sourceSystem, String fileName, byte[] file, UUID adminId) {
        if (sourceSystem == null || sourceSystem.isBlank() || sourceSystem.length() > MAX_SOURCE_SYSTEM_LENGTH) {
            throw new InvalidImportRequestException(
                "Kaynak sistem adı boş olamaz ve en fazla " + MAX_SOURCE_SYSTEM_LENGTH + " karakter olabilir");
        }
        if (tenantLookupPort.findTenantName(tenantId).isEmpty()) {
            throw new ImportTenantNotFoundException(tenantId);
        }
        ParsedWorkbook workbook;
        try {
            workbook = parser.parse(new ByteArrayInputStream(file));
        } catch (InvalidImportFileException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidImportFileException("Dosya okunamadı: " + e.getMessage(), e);
        }
        String system = canonicalSourceSystem(tenantId, sourceSystem.trim());
        ImportReport report = validator.validate(tenantId, system, workbook, lookup);
        int totalRows = workbook.owners().size() + workbook.patients().size();
        return batchRepository.save(ImportBatch.preview(tenantId, system, fileName, file, report, totalRows, adminId));
    }

    /**
     * "Daha once aktarilmis" kontrolu kaynak sistem adina gore yapilir; serbest metin "kolayvet" / "Kolay Vet" ile
     * "KolayVet" ayri sistem sayilmasin diye kiracinin daha once kullandigi, katlanmis hali (Turkce kucuk harf,
     * bosluksuz) ayni olan yazim yeniden kullanilir. Birden fazla varsa ilk kullanilan secilir.
     */
    private String canonicalSourceSystem(UUID tenantId, String requested) {
        String key = sourceSystemKey(requested);
        for (String existing : batchRepository.findSourceSystems(tenantId)) {
            if (sourceSystemKey(existing).equals(key)) {
                return existing;
            }
        }
        return requested;
    }

    static String sourceSystemKey(String sourceSystem) {
        return TurkishText.fold(sourceSystem).replaceAll("\\s+", "");
    }
}
