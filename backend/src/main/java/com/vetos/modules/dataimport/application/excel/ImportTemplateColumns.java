package com.vetos.modules.dataimport.application.excel;

import java.util.List;

/** Sablon sayfalari ve sutunlari. Zorunlu sutunlar {@code *_REQUIRED} listelerindedir. */
final class ImportTemplateColumns {

    static final String OWNERS_SHEET = "Sahipler";
    static final String PATIENTS_SHEET = "Hastalar";

    static final List<String> OWNER_COLUMNS = List.of(
            "kaynak_id", "ad_soyad", "telefon", "ikinci_telefon", "e_posta", "tc_kimlik", "adres", "il", "ilce", "notlar");
    static final List<String> OWNER_REQUIRED = List.of("kaynak_id", "ad_soyad", "telefon");

    static final List<String> PATIENT_COLUMNS = List.of(
            "kaynak_id", "sahip_kaynak_id", "ad", "tur", "irk", "cinsiyet", "dogum_tarihi", "kisirlastirilmis",
            "mikrocip_no", "pasaport_no", "renk", "notlar");
    static final List<String> PATIENT_REQUIRED = List.of("kaynak_id", "sahip_kaynak_id", "ad", "tur");

    private ImportTemplateColumns() {}
}
