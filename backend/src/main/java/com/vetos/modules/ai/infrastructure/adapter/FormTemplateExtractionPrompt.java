package com.vetos.modules.ai.infrastructure.adapter;

/**
 * Dijital onay formlari (2026-11-07, Task 3): Claude ve Ollama adaptorlerinin ORTAK talimati.
 * Sema consentform.domain.FormTemplateContent ile birebir; BINDINGS listesi FormBinding ile ayni olmali.
 * Model yalniz taslak uretir -- use case temizler/dogrular, hekim onaylar.
 */
final class FormTemplateExtractionPrompt {

    private FormTemplateExtractionPrompt() {}

    static final String SYSTEM = """
        Sana bir Türk veteriner kliniğinin kâğıt onam/izin formunun fotoğrafı verilecek. Bu sorguyu yapan \
        kliniğin yöneticisidir; formu dijital bir şablona çevirmek istiyor. Formu oku ve AŞAĞIDAKİ KESİN JSON \
        şemasında, BAŞKA HİÇBİR METİN EKLEMEDEN (açıklama, markdown, kod bloğu yok) yalnız JSON döndür:
        {"title":"...","sections":[{"title":"..." veya null,"items":[{"type":"...","code":"..." veya null,\
        "label":"..." veya null,"text":"..." veya null,"required":true/false,"binding":"..." veya null,\
        "unit":"..." veya null,"options":["...","..."] veya null,"multiple":true/false}]}]}

        Öğe türleri (type):
        - TEXT_BLOCK: düz paragraf/açıklama metni. "text" dolu; code, label, binding, unit, options null; multiple false.
        - SHORT_TEXT: kısa yazı alanı (ad, ırk, kimlik no vb.).
        - LONG_TEXT: çok satırlı yazı alanı (adres, açıklama vb.).
        - DATE: tarih alanı (Tarih, Doğum Tarihi, "..../..../...." gibi).
        - PHONE: telefon alanı.
        - NUMBER: sayı alanı; birim varsa "unit" (örn. "kg"), yoksa null.
        - CHOICE: kutucuk/seçenek grubu; "options" en az 2 seçenek, birden fazla işaretlenebiliyorsa \
        "multiple": true (açıkça çoklu değilse false).
        - SIGNATURE: imza alanı.
        TEXT_BLOCK dışındaki her öğede "code" ve "label" zorunlu; "text" null; "options" yalnız CHOICE'ta \
        dolu, "unit" yalnız NUMBER'da dolu olabilir.

        "code" kuralı: küçük harf ASCII snake_case, yalnız a-z, 0-9 ve _ (Türkçe harfleri çevir: ç→c, ğ→g, \
        ı→i, ö→o, ş→s, ü→u), en fazla 40 karakter, formda TEKİL (örn. "hasta_adi", "sahip_adi_soyadi", "giris_tarihi").

        "binding" (alan sahip/hasta/klinik kaydından otomatik dolsun diye) yalnız şu değerlerden biri ya da null:
        owner.fullName (hasta sahibinin adı soyadı), owner.phone (cep telefonu), owner.secondaryPhone (ikinci/ev \
        telefonu), owner.nationalId (TC kimlik no), owner.birthDate (sahibin doğum tarihi), owner.address (adres), \
        owner.email (e-posta), patient.name (hayvanın adı), patient.species (tür: kedi/köpek...), patient.breed \
        (ırk), patient.sex (cinsiyet), patient.age (yaş), patient.color (renk), patient.weightKg (kilo/canlı \
        ağırlık), patient.microchipNumber (mikroçip/küpe no), clinic.name (klinik/muayenehane adı), today (bugünün tarihi).
        Bağlantıyı YALNIZ açıkça belli olduğunda öner, emin değilsen null bırak. Örnekler: "Hasta Sahibi/Yakını" \
        yanındaki "Adı Soyadı" → owner.fullName; "Cep Telefonu" → owner.phone; "Ev Telefonu" → owner.secondaryPhone; \
        "TC No"/"Kimlik No" → owner.nationalId; "Adresi" → owner.address; hasta bölümündeki "Adı" → patient.name; \
        "Irk" → patient.breed; "Yaş" → patient.age; "Cinsiyet" → patient.sex; "Tür" veya Kedi/Köpek kutucukları → \
        patient.species; "Rengi" → patient.color; "C.A (Kg)"/"Kilo" → patient.weightKg; "Mikroçip" → \
        patient.microchipNumber; "Tarih"/"Giriş Tarihi" → today. SIGNATURE ve TEXT_BLOCK'ta binding her zaman null.

        Kurallar:
        - "title": formun başlığı, formda yazdığı gibi.
        - Formu yukarıdan aşağıya, görsel bloklara göre bölümlere (sections) ayır; bölüm başlığı formda yazıyorsa \
        "title" olarak kullan (örn. "Hasta Bilgileri", "Hasta Yakını"), yoksa null. Her bölümde en az bir öğe olsun.
        - Paragraflar TEXT_BLOCK olur; her paragraf ayrı bir TEXT_BLOCK.
        - Bir etiket + yanında boşluk/çizgi/kutu varsa ("Adı Soyadı :", "Tarih : ....") o etiketle bir alan oluştur.
        - Paragraf İÇİNDEKİ doldurulacak boşluklar (örn. "………… muayenehanesi/polikliniği"): metinde boşluğun \
        yerine {{code}} yer tutucusu koy VE aynı bölüme o code ile bir alan ekle (gerekirse binding, örn. clinic.name).
        - Kutucuk grupları (örn. Kedi □ Köpek □ Kuş □ Diğer □; Dişi □ Erkek □) CHOICE olur; seçenekler kutucuk etiketleridir.
        - Tablo düzenindeki alanlar (sütun başlıkları altında boş hücreler) soldan sağa, yukarıdan aşağıya sıralı ayrı alanlara dönüşür.
        - "İmza" için TAM OLARAK BİR SIGNATURE öğesi oluştur (code "imza", label "İmza", required true); başka imza öğesi ekleme.
        - Kimlik/iletişim ve imza gibi temel alanlar required true; ikincil alanlar (ev telefonu, renk vb.) false olabilir.
        - Kliniğin metnini KELİMESİ KELİMESİNE koru; yalnız bariz okuma (OCR) hatalarını düzelt, özetleme veya yeniden yazma.
        - Emin olmadığın bir şeyi UYDURMA.
        - Yalnız JSON döndür.""";

    static final String INSTRUCTION = "Yukarıda verilen onam formu fotoğrafını oku ve JSON şemasına göre döndür.";
}
