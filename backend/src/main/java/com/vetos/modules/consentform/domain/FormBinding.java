package com.vetos.modules.consentform.domain;

import java.util.Arrays;
import java.util.Optional;

/** Formdaki alanin sahip/hasta/klinik verisine baglantisi. writable: form sonunda kayda geri yazilabilir. */
public enum FormBinding {
    OWNER_FULL_NAME("owner.fullName", false),
    OWNER_PHONE("owner.phone", true),
    OWNER_SECONDARY_PHONE("owner.secondaryPhone", true),
    OWNER_NATIONAL_ID("owner.nationalId", true),
    OWNER_BIRTH_DATE("owner.birthDate", true),
    OWNER_ADDRESS("owner.address", true),
    OWNER_EMAIL("owner.email", true),
    PATIENT_NAME("patient.name", false),
    PATIENT_SPECIES("patient.species", false),
    PATIENT_BREED("patient.breed", false),
    PATIENT_SEX("patient.sex", false),
    PATIENT_AGE("patient.age", false),
    PATIENT_COLOR("patient.color", true),
    PATIENT_WEIGHT_KG("patient.weightKg", true),
    PATIENT_MICROCHIP_NUMBER("patient.microchipNumber", true),
    CLINIC_NAME("clinic.name", false),
    TODAY("today", false);

    private final String key;
    private final boolean writable;

    FormBinding(String key, boolean writable) {
        this.key = key;
        this.writable = writable;
    }

    public String key() { return key; }

    public boolean writable() { return writable; }

    public boolean supports(FormItemType type) {
        return switch (this) {
            case OWNER_BIRTH_DATE, TODAY -> type == FormItemType.DATE;
            case PATIENT_WEIGHT_KG -> type == FormItemType.NUMBER;
            case OWNER_PHONE, OWNER_SECONDARY_PHONE -> type == FormItemType.PHONE
                || type == FormItemType.SHORT_TEXT || type == FormItemType.LONG_TEXT;
            case PATIENT_SPECIES, PATIENT_BREED, PATIENT_SEX, PATIENT_COLOR -> type == FormItemType.CHOICE
                || type == FormItemType.SHORT_TEXT || type == FormItemType.LONG_TEXT;
            default -> type == FormItemType.SHORT_TEXT || type == FormItemType.LONG_TEXT;
        };
    }

    public static Optional<FormBinding> fromKey(String key) {
        if (key == null) return Optional.empty();
        return Arrays.stream(values()).filter(b -> b.key.equals(key)).findFirst();
    }
}
