package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.FormWriteBackCommand;
import com.vetos.modules.patient.application.dto.FormWriteBackResult;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort;
import com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort.InvalidSourceException;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.TcKimlikValidator;
import com.vetos.modules.patient.domain.event.PatientIdentificationUpdatedEvent;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import com.vetos.modules.patient.domain.exception.PatientNotFoundException;
import com.vetos.modules.patient.domain.exception.PatientOwnerMismatchException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Imzali formdaki musteri onayli degerleri sahip/hasta kaydina yazar. Yalnizca
 * asagidaki 9 baglanti yazilabilir; gecersiz/bos degerler atlanir (skipped).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyFormWriteBacksUseCase {

    // NUMERIC(6,2): pozitif olarak saklanabilen değer ve iki basamağa yuvarlama sınırları.
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("9999.99");
    private static final BigDecimal MIN_ROUNDABLE_WEIGHT_KG = new BigDecimal("0.005");

    private static final Set<String> WRITABLE = Set.of(
        "owner.phone", "owner.secondaryPhone", "owner.nationalId", "owner.birthDate", "owner.address",
        "owner.email", "patient.color", "patient.weightKg", "patient.microchipNumber"
    );

    private final SignedFormWriteBackLookupPort signedFormLookup;
    private final OwnerRepository ownerRepository;
    private final PatientRepository patientRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public FormWriteBackResult execute(FormWriteBackCommand command) {
        if (command.signedFormId() == null) {
            throw new InvalidSourceException();
        }
        Owner owner = ownerRepository.findById(command.ownerId())
            .orElseThrow(() -> new OwnerNotFoundException(command.ownerId()));
        Patient patient = patientRepository.findById(command.patientId())
            .orElseThrow(() -> new PatientNotFoundException(command.patientId()));
        if (!command.ownerId().equals(patient.getOwnerId())) {
            throw new PatientOwnerMismatchException();
        }

        var allowedValues = signedFormLookup.allowedValues(command.signedFormId(), command.patientId(), command.ownerId());
        List<String> applied = new ArrayList<>();
        Set<String> appliedBindings = new HashSet<>();
        List<FormWriteBackResult.Skipped> skipped = new ArrayList<>();
        boolean ownerChanged = false;
        boolean patientChanged = false;
        boolean microchipChanged = false;

        List<FormWriteBackCommand.Item> items = command.items() == null ? List.of() : command.items();
        for (FormWriteBackCommand.Item item : items) {
            String binding = item.binding();
            if (binding == null || !WRITABLE.contains(binding)) {
                skipped.add(new FormWriteBackResult.Skipped(binding, "yazılamaz alan"));
                continue;
            }
            if (appliedBindings.contains(binding)) {
                skipped.add(new FormWriteBackResult.Skipped(binding, "tekrarlanan alan"));
                continue;
            }
            String value = item.value() == null ? "" : item.value().trim();
            if (value.isEmpty()) {
                skipped.add(new FormWriteBackResult.Skipped(binding, "boş değer"));
                continue;
            }
            if (!allowedValues.getOrDefault(binding, Set.of()).contains(value)) {
                skipped.add(new FormWriteBackResult.Skipped(binding, "imzalı formda geri yazılmasına izin verilen değer değil"));
                continue;
            }
            String error = null;
            switch (binding) {
                case "owner.phone" -> owner.updatePhone(value);
                case "owner.secondaryPhone" -> owner.updateSecondaryPhone(value);
                case "owner.address" -> owner.updateAddress(value);
                case "owner.email" -> {
                    if (isBasicEmail(value)) {
                        owner.updateEmail(value);
                    } else {
                        error = "geçersiz e-posta";
                    }
                }
                case "owner.nationalId" -> {
                    if (TcKimlikValidator.isValid(value)) {
                        owner.updateNationalId(value);
                    } else {
                        error = "geçersiz TC kimlik no";
                    }
                }
                case "owner.birthDate" -> {
                    LocalDate date = parseDate(value);
                    if (date == null || date.isAfter(LocalDate.now())) {
                        error = "geçersiz doğum tarihi";
                    } else {
                        owner.updateBirthDate(date);
                    }
                }
                case "patient.color" -> patient.updateColor(value);
                case "patient.weightKg" -> {
                    BigDecimal weight = parseWeight(value);
                    if (weight == null) {
                        error = "geçersiz kilo";
                    } else {
                        patient.recordWeight(weight);
                    }
                }
                case "patient.microchipNumber" -> {
                    patient.updateMicrochip(value);
                    microchipChanged = true;
                }
                default -> error = "yazılamaz alan";
            }
            if (error != null) {
                skipped.add(new FormWriteBackResult.Skipped(binding, error));
                continue;
            }
            applied.add(binding);
            appliedBindings.add(binding);
            if (binding.startsWith("owner.")) {
                ownerChanged = true;
            } else {
                patientChanged = true;
            }
        }

        if (ownerChanged) {
            ownerRepository.save(owner);
        }
        if (patientChanged) {
            patientRepository.save(patient);
        }
        if (microchipChanged) {
            // UpdatePatientIdentificationUseCase ile ayni olay (TARBIL tarafi icin)
            eventPublisher.publish(new PatientIdentificationUpdatedEvent(
                patient.getId(), patient.getMicrochipNumber(), patient.getPassportNumber()
            ));
        }
        log.info("Form geri yazma: signedFormId={} hasta={} uygulanan={} atlanan={}",
            command.signedFormId(), command.patientId(), applied.size(), skipped.size());
        return new FormWriteBackResult(applied, skipped);
    }

    private static boolean isBasicEmail(String v) {
        int at = v.indexOf('@');
        return at > 0 && at == v.lastIndexOf('@') && at < v.length() - 1 && v.indexOf(' ') < 0;
    }

    private static LocalDate parseDate(String v) {
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal parseWeight(String v) {
        try {
            BigDecimal d = new BigDecimal(v.replace(',', '.'));
            if (d.compareTo(MIN_ROUNDABLE_WEIGHT_KG) < 0 || d.compareTo(MAX_WEIGHT_KG) > 0) return null;
            return d.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }
}
