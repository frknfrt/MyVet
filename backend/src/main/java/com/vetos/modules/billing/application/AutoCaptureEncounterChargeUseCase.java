package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceLineSource;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @docs/architecture.md Bolum 4: EncounterFinalizedEvent dinlenerek otomatik
 * charge capture yapilir -- muayene bittiginde receptionist'in "faturayi
 * unutmasi" riski ortadan kalkar. Tutar bilerek 0 birakilir; hangi
 * hizmet/urunlerin kullanildigi bilgisi encounter'da tutulmadigi icin
 * (inventory modulu Faz 1 Modul 6'da eklenecek), resepsiyonist DRAFT
 * faturayi Finans sayfasindan tutari girip kesene kadar duzenleyebilir.
 */
@Service
@RequiredArgsConstructor
public class AutoCaptureEncounterChargeUseCase {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PatientLookupPort patientLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional
    public UUID execute(UUID encounterId, UUID patientId, UUID staffUserId) {
        var patient = patientLookupPort.findSummaryById(patientId);
        var staff = staffUserLookupPort.findSummaryById(staffUserId);

        Invoice invoice = invoiceRepository.save(
            Invoice.createDraft(TenantContext.current(), staff.branchId(), patient.ownerId(), encounterId)
        );
        invoiceLineRepository.save(InvoiceLine.create(
            invoice.getId(), "Muayene ucreti (tutari guncelleyin)", 1, BigDecimal.ZERO,
            null, null, InvoiceLineSource.AUTO_CHARGE_CAPTURE
        ));
        return invoice.getId();
    }
}
