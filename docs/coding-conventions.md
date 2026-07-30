# Kod Konvansiyonları

Bu doküman "nasıl" sorusuna cevap verir — mimari kararlar (neden port/adapter, neden modüler monolit) için @docs/architecture.md'ye bakın. Burada sadece **kesin, tartışmasız kurallar** var; her yeni sınıf/dosya bu kurallara göre adlandırılır.

## Paket ve Dosya Şablonu

Her modül **birebir** bu yapıda:

```
com.vetos.modules.<modul_adi>/
├── domain/
│   ├── <Entity>.java                 # JPA entity, sadece bu pakette
│   ├── <Entity>Id.java               # Value object (UUID wrapper), gerekirse
│   ├── <ModulAdi>Repository.java     # Port (interface) — TAM CRUD, sadece kendi modulu kullanir
│   ├── <ModulAdi>LookupPort.java     # Port (interface) — SADECE OKUMA, diger moduller kullanir
│   └── event/
│       └── <Entity><Fiil>Event.java  # Domain event, orn. EncounterFinalizedEvent
├── application/
│   ├── Create<Entity>UseCase.java
│   ├── Update<Entity>UseCase.java
│   ├── Get<Entity>UseCase.java
│   └── dto/
│       ├── Create<Entity>Command.java   # Use-case input (record)
│       └── <Entity>Summary.java         # Use-case output (record)
├── infrastructure/
│   ├── persistence/
│   │   ├── <Entity>JpaRepository.java   # Spring Data interface
│   │   └── <ModulAdi>RepositoryAdapter.java  # Port implementasyonu
│   └── event/
│       └── <DigerModul><Entity>EventListener.java  # Baska modulun event'ini dinler
└── api/
    ├── <ModulAdi>Controller.java
    └── dto/
        ├── <Entity>Request.java   # HTTP request body (record)
        └── <Entity>Response.java  # HTTP response body (record)
```

**Kural:** `domain` ve `application` paketleri **framework-agnostic** kalmaya çalışır (Spring anotasyonu sadece Repository/Service seviyesinde, iş mantığında değil). `infrastructure` ve `api` Spring'e bağımlı olabilir.

## İsimlendirme

| Öğe | Kural | Örnek |
|---|---|---|
| Entity | Tekil isim, ER diyagramındaki adla birebir aynı | `Patient`, `Encounter`, `InvoiceLine` |
| Repository port (tam CRUD) | `<Entity>Repository` | `PatientRepository` |
| Lookup port (sadece okuma, cross-module) | `<Entity>LookupPort` | `PatientLookupPort` |
| Use-case | `<Fiil><Entity>UseCase` — fiil: Create/Update/Delete/Get/List/Finalize | `CreateEncounterUseCase` |
| Command (use-case input) | `<Fiil><Entity>Command` | `CreateEncounterCommand` |
| Domain event | `<Entity><GeçmişZamanFiil>Event` | `EncounterFinalizedEvent` |
| REST controller | `<ModulAdi>Controller` (çoğul modül adı) | `PatientsController` |
| Request DTO | `<Entity>Request` | `CreatePatientRequest` |
| Response DTO | `<Entity>Response` | `PatientResponse` |
| Custom exception | `<Durum>Exception`, `DomainException`'dan türer | `PatientNotFoundException` |

**Yasak:** Kısaltma yok (`Repo`, `Svc`, `Ctrl` gibi kısaltmalar kullanılmaz — `Repository`, `Service`, `Controller` tam yazılır). `Impl` son eki yasak — adapter sınıfları işlevini anlatan isim alır (`PatientRepositoryAdapter`, `PatientRepositoryImpl` değil).

## Exception Stratejisi

Tüm domain exception'ları `com.vetos.platform.exception.DomainException` sınıfından türer:

```java
public abstract class DomainException extends RuntimeException {
    private final String errorCode; // orn. "PATIENT_NOT_FOUND"
    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public String getErrorCode() { return errorCode; }
}
```

- Her modül kendi `<Modul>Exception` alt sınıflarını `domain/exception/` altında tanımlar.
- `@RestControllerAdvice` ile TEK bir global exception handler (`platform/web/GlobalExceptionHandler.java`) tüm `DomainException`'ları yakalayıp @docs/api-conventions.md'deki standart hata formatına çevirir.
- Controller katmanında ASLA `try/catch` ile exception yutulmaz.

## Validation

- Request DTO'larında Jakarta Bean Validation anotasyonları (`@NotBlank`, `@Email`, `@Size`) kullanılır.
- İş kuralı validasyonu (örn. "bu tarihte hekimin başka randevusu var mı") use-case katmanında yapılır, controller'da değil.

## Logging

- SLF4J + Lombok `@Slf4j`. `System.out.println` YASAK.
- Log seviyeleri: `error` (beklenmeyen hata), `warn` (iş kuralı ihlali, örn. stok yetersiz), `info` (önemli iş olayı: randevu oluşturuldu, ödeme alındı), `debug` (geliştirme detayı).
- PII (hasta sahibi telefon/e-posta, TC kimlik gibi) LOG'A YAZILMAZ.

## Test İsimlendirme

`should_<beklenenSonuç>_when_<koşul>` formatı:

```java
@Test
void should_throwPatientNotFoundException_when_patientIdDoesNotExist() { ... }
```

Use-case testleri port'ları mock'lar (Mockito), gerçek veritabanına dokunmaz. Repository adapter testleri `@DataJpaTest` ile gerçek (test) veritabanını kullanır.
