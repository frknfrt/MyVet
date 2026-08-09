# MyVet — Spring Boot Mimari Tasarımı (SOLID Odaklı)

## 1. Genel Yaklaşım: Hexagonal Mimarili Modüler Monolit

**Karar:** Başlangıçta mikroservise değil, **modül sınırları net çizilmiş bir modüler monolit**e gidiyoruz. Her modül kendi içinde **hexagonal (ports & adapters)** mimariyle yazılır. Bu, hem SOLID'i doğal olarak zorlar hem de ileride bir modülü (örn. `ai` veya `notification`) ayrı bir servise çıkarmayı düşük maliyetli hale getirir — çünkü modül zaten dış dünyayla sadece arayüzler (port) üzerinden konuşuyor olacak.

**Araç önerisi:** [Spring Modulith](https://spring.io/projects/spring-modulith) kullanımı — modüller arası izinsiz bağımlılığı derleme/test aşamasında yakalayan resmi bir Spring projesi. `ApplicationModules.of(VetosApplication.class).verify()` gibi bir testle "patient modülü appointment modülünün internal sınıfına doğrudan erişemez" kuralını otomatik doğrularsın.

## 2. Üst Seviye Paket Yapısı

```
com.vetos
├── platform/                    # Cross-cutting: hicbir domain moduluyle ilgilenmez
│   ├── security/                 # JWT, rol bazli yetkilendirme
│   ├── tenancy/                  # TenantContext, multi-tenant filtreleme
│   ├── audit/                    # AuditLog altyapisi
│   └── event/                    # Modul-ici event bus altyapisi (Spring ApplicationEvent)
│
├── modules/
│   ├── patient/
│   │   ├── domain/               # Entity, Value Object, domain event, PORT interface'leri
│   │   ├── application/          # Use-case siniflari (servis degil!), DTO, mapper
│   │   ├── infrastructure/       # JPA repo implementasyonu, disari cikan adapter'lar
│   │   └── api/                  # REST controller, request/response modelleri
│   │
│   ├── appointment/               (ayni ic yapi)
│   ├── encounter/                 # SOAP kayitlari (klinik modulu)
│   ├── billing/
│   ├── inventory/
│   ├── notification/              # Bildirim kanallari (SMS/WhatsApp/Push/Email)
│   ├── ai/                        # AI Gateway - saglayici bagimsiz
│   ├── integration/
│   │   ├── tarbil/
│   │   └── einvoice/
│   └── tenant/                    # Klinik/sube/abonelik yonetimi
│
└── VetosApplication.java
```

Her modülün **kendi `domain` paketi dışına hiçbir entity sızmaz.** Bir modül başka bir modülün verisine ihtiyaç duyduğunda ya o modülün **application katmanındaki use-case arayüzünü** çağırır, ya da **domain event** dinler — asla diğer modülün JPA entity'sine doğrudan repository ile erişmez. Bu tek kural, SOLID'in D (Dependency Inversion) ve I (Interface Segregation) maddelerinin modül seviyesindeki karşılığı.

## 3. SOLID'in Her Maddesi Somut Olarak Nasıl Uygulanıyor

### S — Single Responsibility

Klasik "her şeyi yapan `PatientService`" anti-pattern'inden kaçınıyoruz. Her iş kuralı kendi **use-case sınıfında** yaşar:

```java
// application/CreateEncounterUseCase.java
public class CreateEncounterUseCase {
    private final EncounterRepository encounterRepository;   // port
    private final PatientLookupPort patientLookupPort;       // port (baska modul)
    private final DomainEventPublisher eventPublisher;

    public EncounterId execute(CreateEncounterCommand command) {
        var patient = patientLookupPort.findById(command.patientId());
        var encounter = Encounter.start(patient.id(), command.staffUserId());
        encounterRepository.save(encounter);
        eventPublisher.publish(new EncounterStartedEvent(encounter.id()));
        return encounter.id();
    }
}
```

Bir sınıf = bir iş kuralı = bir değişme sebebi. `UpdateEncounterUseCase`, `FinalizeSoapUseCase` gibi ayrı sınıflar, tek bir "tanrı servis" yerine.

### O — Open/Closed (en kritik nokta: AI ve entegrasyonlar)

Yeni bir AI sağlayıcı, ödeme altyapısı veya bildirim kanalı eklemek, **var olan kodu değiştirmeden** yapılabilmeli. Bunu strateji deseniyle çözüyoruz:

```java
// modules/ai/domain/SoapGenerationPort.java
public interface SoapGenerationPort {
    SoapDraft generateFromAudio(AudioTranscript transcript);
    boolean supports(AiTaskType taskType);
}

// infrastructure/adapters/AnthropicSoapAdapter.java
@Component
public class AnthropicSoapAdapter implements SoapGenerationPort { ... }

// infrastructure/adapters/SelfHostedSoapAdapter.java
@Component
public class SelfHostedSoapAdapter implements SoapGenerationPort { ... }

// application/AiGatewayRouter.java
public class AiGatewayRouter {
    private final List<SoapGenerationPort> providers; // Spring hepsini otomatik toplar
    public SoapDraft generate(AudioTranscript t, AiTaskType type) {
        return providers.stream()
            .filter(p -> p.supports(type))
            .findFirst()
            .orElseThrow()
            .generateFromAudio(t);
    }
}
```

Yeni bir sağlayıcı (örn. self-hosted Llama modeli) eklemek için tek yapılan: yeni bir `@Component` sınıfı yazmak. `AiGatewayRouter` tek satır değişmez. Aynı desen **`NotificationChannel`** (SMS/WhatsApp/Push/Email) ve **`PaymentGatewayPort`** (iyzico/PayTR) için de kullanılacak — geçen sohbette konuştuğumuz "API mi self-hosted mi" kararını koda hiç dokunmadan değiştirebilmenin teknik temeli tam olarak bu.

### L — Liskov Substitution

Tür bazlı SOAP şablonları (kedi/köpek/büyükbaş/egzotik) için ortak bir soyutlama:

```java
public abstract class ExaminationTemplate {
    public abstract List<VitalField> requiredVitals();
    public abstract boolean isApplicableTo(Species species);
}
```

Her alt sınıf (`LargeAnimalTemplate`, `SmallAnimalTemplate`) üst sınıfın sözleşmesini bozmadan davranmalı — örn. `requiredVitals()` hiçbir zaman `null` dönmemeli, boş liste dönebilir. Bu, tür bazlı davranış farklarını `if (species == COW)` gibi dallanmalarla değil, polimorfizmle çözer.

### I — Interface Segregation

Geniş bir `PatientRepository` (30 metotlu) yerine, ihtiyaca göre bölünmüş portlar:

```java
public interface PatientLookupPort {          // sadece okuma, baska moduller icin
    PatientSummary findById(PatientId id);
}
public interface PatientRepository extends    // yazma + tam CRUD, sadece patient modulu icinde
    PatientLookupPort {
    void save(Patient patient);
    void delete(PatientId id);
}
```

`billing` modülü hasta oluşturamaz/silemez — sadece `PatientLookupPort`'u görür. Yanlışlıkla yapılabilecek bir işlemi derleme zamanında imkansız hale getiriyoruz.

### D — Dependency Inversion

`application` katmanı hiçbir zaman `infrastructure` katmanını import etmez — sadece `domain` katmanındaki port arayüzlerini bilir. Somut implementasyon (JPA, Anthropic SDK, TARBİL SOAP client) `infrastructure`'da yaşar ve Spring DI ile enjekte edilir. Bu sayede:
- Use-case sınıflarını **veritabanı olmadan, mock port'larla** birim test edebiliyoruz.
- PostgreSQL'den başka bir veritabanına geçiş, sadece `infrastructure/persistence` paketini etkiler.

## 4. Modüller Arası İletişim: Doğrudan Çağrı Değil, Domain Event

Örnek: Bir `Encounter` tamamlandığında hem `billing` (fatura kalemi oluştur) hem `inventory` (stok düş) hem `tarbil` (bildirim gönder) tepki vermeli. Bunu `EncounterFinalizeUseCase`'in içine üç modülün kodu yazılarak değil, event ile çözüyoruz:

```java
// encounter modulu yayinlar
eventPublisher.publish(new EncounterFinalizedEvent(encounterId, patientId, usedItems));

// billing modulu dinler (kendi paketinde, encounter modulunu import etmeden)
@EventListener
void onEncounterFinalized(EncounterFinalizedEvent event) {
    createInvoiceLinesFromEncounter(event);
}
```

`encounter` modülü `billing`'in var olduğunu bile bilmez. Yarın yeni bir modül (örn. `loyalty` — sadakat puanı) eklendiğinde, sadece aynı event'i dinleyen yeni bir `@EventListener` yazılır; `encounter` modülüne hiç dokunulmaz. Bu, Open/Closed prensibinin modüller arası uygulanışı.

## 5. Genişletilebilirlik Noktaları (Özet Tablo)

| Genişletme ihtiyacı | Mekanizma | Yeni kod eklerken değişmesi gerekmeyen yer |
|---|---|---|
| Yeni AI sağlayıcı (self-hosted, farklı LLM) | `SoapGenerationPort` implementasyonu | `AiGatewayRouter` |
| Yeni bildirim kanalı (örn. Telegram) | `NotificationChannel` implementasyonu | `NotificationService` |
| Yeni ödeme sağlayıcısı | `PaymentGatewayPort` implementasyonu | `PaymentService` |
| Yeni tür bazlı muayene şablonu | `ExaminationTemplate` alt sınıfı | `ExaminationTemplateResolver` |
| Yeni resmi entegrasyon (örn. ileride başka bir bakanlık sistemi) | `integration/` altına yeni modül | Diğer tüm modüller |
| Mikroservise ayrıştırma (ileride) | Modül zaten port/adapter ile izole, event-driven | İş mantığı değişmez, sadece transport (HTTP/queue) eklenir |

## 6. Sınırların Bozulmasını Engelleme

- **Spring Modulith test:** CI pipeline'da her PR'da `ApplicationModules.verify()` çalışır — bir geliştirici yanlışlıkla `billing`'den `patient.infrastructure.PatientJpaEntity`'yi import ederse build kırılır.
- **Paket görünürlüğü:** `domain` ve `application` sınıfları `public`, `infrastructure` içindeki implementasyon sınıfları mümkün olduğunca **package-private** — dışarıdan sadece port arayüzü görünür, implementasyon detayı görünmez.
- **Code review kuralı:** Bir modülün `api` (controller) katmanı, başka bir modülün `domain` paketini asla import etmemeli.

### 6.1 Kenar durum: Platform Admin'in yazma erişimi (Faz 2)

`LookupPort` deseni ("diğer modüller sadece okur") tek bir bilinçli istisnayla genişletildi: `modules/tenant/domain/TenantAdminPort.java`, `modules.platformadmin` modülünün herhangi bir kiracıyı görüntüleyip **değiştirebilmesi** için okuma yanında yazma metotları da içerir (abonelik/durum güncelleme). Bu, platform admin'in tanımı gereği (SaaS operatörü, tüm kiracıları yönetir) gerekli — normal bir iş modülü (örn. `billing`) için asla bu deseni kullanma, sadece `modules.platformadmin` bu tür bir port'a sahip olabilir. Yeni bir "admin tarafı" ihtiyaç doğarsa aynı isimlendirme deseni (`XxxAdminPort`) izlenir.

## 7. Sonraki Adım Önerisi

Bu mimari iskeletini artık somut koda dökebiliriz — örneğin `patient` ve `encounter` modüllerini örnek olarak tam paket yapısıyla (entity, port, use-case, adapter, controller) yazıp bir başlangıç şablonu (boilerplate) çıkarabiliriz, ya da önce Spring Modulith testiyle bu sınırları doğrulayan bir iskelet proje (skeleton repo yapısı) kurabiliriz.
