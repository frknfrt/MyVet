package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma -- REVIZE
 * (2026-10-31). UnsupportedOcrFileTypeException'dan FARKI: o sadece declare
 * edilen Content-Type'in "image/" ile baslayip baslamadigina bakar (ornegin
 * bir PDF'i eler), bu ise dosyanin GERCEK baytlarini (bkz. OcrImageFormatDetector)
 * inceleyip AI saglayicisinin (Claude/Ollama) FIILEN islemeyecegi bir gorsel
 * formatini (ozellikle iPhone HEIC/HEIF, veya BMP/TIFF) erken ve ACIK bir
 * mesajla eler -- boylece kullanici "AI modeline baglanilamadi" gibi yanlis/
 * belirsiz bir hata yerine NE YAPMASI gerektigini gorur. Suffix "Exception" ->
 * GlobalExceptionHandler 422 doner.
 */
public class UnsupportedOcrImageFormatException extends DomainException {
    public UnsupportedOcrImageFormatException(UUID fileId, boolean heic) {
        super("UNSUPPORTED_OCR_IMAGE_FORMAT", heic
            ? "Bu fotograf HEIC/HEIF formatinda, AI bu formati okuyamiyor (dosya: " + fileId + "). "
                + "Lutfen fotografi JPEG veya PNG olarak kaydedip yeniden yukleyin (iPhone'da Ayarlar > Kamera > "
                + "Formatlar menusunden 'Uyumlu' secip fotografi tekrar cekebilir, ya da fotografin ekran "
                + "goruntusunu alip onu yukleyebilirsiniz)."
            : "Bu dosyanin gorsel formati AI tarafindan desteklenmiyor (dosya: " + fileId + "). "
                + "Lutfen JPEG, PNG, GIF veya WEBP formatinda bir dosya yukleyin, ya da fotografin ekran "
                + "goruntusunu alip onu yukleyin.");
    }
}
