package com.vetos.modules.consentform.domain;

/** Ortak başlangıç metni; yönetici kliniğin gerçek işleyişine göre düzenleyebilir. */
public final class CommonPrivacyNotice {
    private CommonPrivacyNotice() {}
    public static PrivacyNotice forClinic(String clinicName) {
        return new PrivacyNotice(1, """
            KİŞİSEL VERİLERİN İŞLENMESİNE İLİŞKİN AYDINLATMA METNİ
            Veri sorumlusu: %s

            Hasta sahibi ve formu imzalayan kişiye ait ad-soyad, iletişim bilgileri, hizmet ve ödeme kayıtları ile imza; hasta/sahip kaydının oluşturulması, veterinerlik hizmetinin yürütülmesi, randevu ve tedavi takibi, faturalandırma ve hukuki yükümlülüklerin yerine getirilmesi amacıyla işlenir. Kimlik bilgileri yalnız işlemin gerektirdiği ölçüde alınır.

            Veriler; tarafınızca verilen bilgiler, klinik çalışanlarının kayıtları, kâğıt veya elektronik formlar üzerinden toplanır. Hizmet ilişkisinin kurulması ve yürütülmesi için gerekli işlemler Kanun'un 5/2-c; kanuni kayıt ve bildirim yükümlülükleri 5/2-ç; bir hakkın tesisi, kullanılması veya korunması için gerekli kayıtlar 5/2-e hükümlerine dayanır. Bu bilgilendirme beyanı açık rıza yerine geçmez.

            Hizmetin sunumu için gereken kayıtlar, amaçla sınırlı olarak kliniğin kullandığı kayıt/barındırma altyapısını sağlayan hizmet sağlayıcılarına; mevzuatın gerektirdiği kayıtlar yetkili kamu kurumlarına aktarılır. Laboratuvar, sigorta veya başka bir sağlayıcıyla farklı bir aktarım yapılacaksa buna ilişkin bilgilendirme ve gerekli hukuki şartlar ayrıca sağlanır. Bu metin yurt dışına aktarım izni oluşturmaz.

            Seçtiğiniz SMS, WhatsApp ve e-posta kanalları yalnız randevu, aşı, tedavi takibi ve hizmet bilgilendirmeleri içindir. Bu seçimler reklam veya kampanya izni değildir; tercihlerinizi kliniğe başvurarak değiştirebilirsiniz. Bir kanalı seçmemeniz veterinerlik hizmetinden yararlanmanıza engel değildir.

            Kanun'un 11. maddesi kapsamında; verilerinizin işlenip işlenmediğini öğrenme, işlenmişse bilgi talep etme, amacını ve amacına uygun kullanılıp kullanılmadığını öğrenme, aktarıldığı üçüncü kişileri bilme, eksik/yanlış bilgilerin düzeltilmesini, şartları varsa silinmesini veya yok edilmesini isteme ve bu işlemlerin aktarılan üçüncü kişilere bildirilmesini talep etme, otomatik analizle aleyhinize çıkan sonuca itiraz etme ve hukuka aykırı işlem nedeniyle zararın giderilmesini talep etme haklarınız vardır. Başvurunuzu veri sorumlusu kliniğe, Veri Sorumlusuna Başvuru Usul ve Esasları Hakkında Tebliğ'deki yöntemlerle iletebilirsiniz.
            """.formatted(clinicName));
    }
}
