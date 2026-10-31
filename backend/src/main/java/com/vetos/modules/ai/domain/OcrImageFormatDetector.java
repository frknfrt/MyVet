package com.vetos.modules.ai.domain;

import java.nio.charset.StandardCharsets;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29).
 *
 * REVIZE (2026-10-31, gercek kullanici bulgusu): bir veteriner, laboratuvar
 * cihazi ekranindan veya termal yazici ciktisindan telefonla CEKILEN HAM
 * fotograflari yukledeginde AI "modele baglanilamadi" hatasi aliyordu, ama
 * AYNI goruntunun EKRAN GORUNTUSUNU alip yukleyince calisiyordu. Kok sebep:
 * Claude/Ollama vision API'leri SADECE JPEG/PNG/GIF/WEBP kabul eder (ornegin
 * Anthropic Messages API image blogu icin "media_type" alani baska bir deger
 * aldiginda 400 invalid_request_error doner); bir telefon kamera fotografi
 * HEIC/HEIF olabilir (iPhone varsayilani) veya tarayici/galeri uygulamasinin
 * bildirdigi Content-Type ile dosyanin GERCEK baytlari UYUSMAYABILIR --
 * ReadLabResultFromImageUseCase/ReadImagingFindingsFromImageUseCase eskiden
 * sadece "contentType.startsWith(\"image/\")" diye GUVENEREK kontrol ediyordu,
 * bu da yanlis/desteklenmeyen formatlarin AI'ya kadar gidip -- hem bosuna kota
 * harcamasina hem de kullaniciya "model erisilemez" gibi YANLIS bir mesaj
 * gosterilmesine (gercek sebep format, baglanti degil) yol aciyordu.
 *
 * Bu siniflandirici dosyanin bildirdigi Content-Type'a GUVENMEDEN, ilk
 * baytlarindaki "magic number"lara bakarak gercek formati tespit eder; ekran
 * goruntuleri (PNG) ve normal foto makinesi/galeri JPEG'leri hep gecer, HEIC/
 * HEIF/BMP/TIFF/PDF vb. formatlar (ve bozuk/bos dosyalar) reddedilir.
 */
public final class OcrImageFormatDetector {

    public enum Format { JPEG, PNG, GIF, WEBP, HEIC, UNKNOWN }

    private OcrImageFormatDetector() {
    }

    /** Dosyanin ilk baytlarindan GERCEK formatini tespit eder; declare edilen contentType'a bakmaz. */
    public static Format detect(byte[] content) {
        if (content == null || content.length < 12) {
            return Format.UNKNOWN;
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8) {
            return Format.JPEG;
        }
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return Format.PNG;
        }
        if (content[0] == 'G' && content[1] == 'I' && content[2] == 'F' && content[3] == '8') {
            return Format.GIF;
        }
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
            && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return Format.WEBP;
        }
        if (content[4] == 'f' && content[5] == 't' && content[6] == 'y' && content[7] == 'p') {
            String boxType = new String(content, 8, 4, StandardCharsets.US_ASCII);
            if (boxType.startsWith("hei") || boxType.startsWith("hev") || boxType.equals("mif1") || boxType.equals("msf1")) {
                return Format.HEIC;
            }
        }
        return Format.UNKNOWN;
    }

    /** Claude Messages API ve Ollama /api/generate'in ikisinin de islebildigi formatlar. */
    public static boolean isSupportedByVisionApis(Format format) {
        return format == Format.JPEG || format == Format.PNG || format == Format.GIF || format == Format.WEBP;
    }

    /** AI saglayicisina gonderilecek GERCEK media_type -- declare edilen (ve yanlis olabilen) contentType degil. */
    public static String mediaTypeFor(Format format) {
        return switch (format) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case GIF -> "image/gif";
            case WEBP -> "image/webp";
            case HEIC, UNKNOWN -> throw new IllegalArgumentException("Desteklenmeyen format icin media_type istenemez: " + format);
        };
    }
}
