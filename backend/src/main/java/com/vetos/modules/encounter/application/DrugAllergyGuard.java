package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.AllergyAcknowledgement;
import com.vetos.modules.encounter.application.dto.AllergyCheckResult;
import com.vetos.modules.encounter.domain.DrugAllergyMatcher;
import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.StockItemNameLookupPort;
import com.vetos.modules.encounter.domain.exception.AllergyConfirmationRequiredConflictException;
import com.vetos.modules.encounter.domain.exception.AllergyOverrideReasonRequiredConflictException;
import com.vetos.modules.patient.domain.PatientClinicalProfile;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Recete ve muayene malzemesi eklenirken hastanin kritik uyarisina gore alerji kontrolu.
 * Uyari varsa her eklemede onay ister; ilac uyariyla eslesirse (bkz. DrugAllergyMatcher) hekim gerekcesi zorunludur ve
 * islem gecmisine yazilir. Arayuz once check() ile sorar; enforce() arayuz atlanirsa diye sunucu tarafi guvencedir.
 */
@Service
@RequiredArgsConstructor
public class DrugAllergyGuard {

    private static final int MIN_REASON_LENGTH = 3;

    private final PatientLookupPort patientLookupPort;
    private final DrugCatalogRepository drugCatalogRepository;
    private final StockItemNameLookupPort stockItemNameLookupPort;
    private final AuditPort auditPort;

    @Transactional(readOnly = true)
    public AllergyCheckResult check(UUID patientId, List<UUID> drugIds, List<UUID> inventoryItemIds) {
        String alert = alertOf(patientId);
        if (alert == null) return new AllergyCheckResult(null, List.of());
        return new AllergyCheckResult(alert, DrugAllergyMatcher.match(alert, labels(drugIds, inventoryItemIds)));
    }

    /** Katalog ilaci "Ad (etken madde)", stok kalemi stoktaki adiyla. */
    public List<String> labels(List<UUID> drugIds, List<UUID> inventoryItemIds) {
        Stream<String> drugs = safe(drugIds).stream()
            .map(id -> drugCatalogRepository.findById(id).map(DrugAllergyGuard::catalogLabel).orElse(null));
        Stream<String> stock = safe(inventoryItemIds).stream()
            .map(id -> stockItemNameLookupPort.findName(id).orElse(null));
        return Stream.concat(drugs, stock).filter(Objects::nonNull).distinct().toList();
    }

    public void enforce(UUID patientId, List<String> drugLabels, AllergyAcknowledgement ack) {
        String alert = alertOf(patientId);
        if (alert == null) return;
        if (ack == null || !ack.acknowledged()) {
            throw new AllergyConfirmationRequiredConflictException(alert);
        }
        List<DrugAllergyMatcher.Match> matches = DrugAllergyMatcher.match(alert, drugLabels);
        if (matches.isEmpty()) return;

        String names = matches.stream().map(DrugAllergyMatcher.Match::drugLabel).collect(Collectors.joining(", "));
        String reason = ack.overrideReason() == null ? "" : ack.overrideReason().trim();
        if (reason.length() < MIN_REASON_LENGTH) {
            throw new AllergyOverrideReasonRequiredConflictException(names);
        }
        String why = matches.stream().map(DrugAllergyMatcher.Match::reason).distinct().collect(Collectors.joining(", "));
        auditPort.record(AuditRecord.of(AuditAction.ALLERGY_WARNING_OVERRIDDEN, "PATIENT", patientId, names,
            "Eşleşme: " + why + " · Gerekçe: " + reason));
    }

    private String alertOf(UUID patientId) {
        return patientLookupPort.findClinicalProfile(patientId)
            .map(PatientClinicalProfile::criticalAlert)
            .filter(a -> !a.isBlank())
            .orElse(null);
    }

    private static String catalogLabel(DrugCatalog drug) {
        String ingredient = drug.getActiveIngredient();
        return ingredient == null || ingredient.isBlank() ? drug.getName() : drug.getName() + " (" + ingredient + ")";
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
