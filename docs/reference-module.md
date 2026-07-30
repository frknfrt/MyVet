# Referans Modül: `patient`

Bu dosya, **yeni bir modül yazarken birebir kopyalanacak somut şablondur.** Buradaki her dosya gerçek, çalışır Java koduna yakın seviyede yazıldı (bazı importlar/getter-setter Lombok ile kısaltıldı). Yeni bir modül (`billing`, `inventory` vb.) yazarken bu dosyayı aç, aynı katman sırasını, aynı isimlendirmeyi izle.

Konvansiyonlar için @docs/coding-conventions.md, mimari gerekçe için @docs/architecture.md.

---

## 1. `domain/Patient.java` (Entity)

```java
package com.vetos.modules.patient.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED) // JPA icin, disaridan new Patient() yasak
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private UUID speciesId;

    private UUID breedId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    private LocalDate birthDate;

    private String microchipNumber;

    @Column(name = "tarbil_animal_id")
    private String tarbilAnimalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatientStatus status;

    // ---- Factory method: entity SADECE bu yolla olusturulur ----
    public static Patient register(UUID tenantId, UUID ownerId, UUID speciesId, String name) {
        Patient p = new Patient();
        p.tenantId = tenantId;
        p.ownerId = ownerId;
        p.speciesId = speciesId;
        p.name = name;
        p.status = PatientStatus.ACTIVE;
        return p;
    }

    // ---- Davranis metotlari, setter degil ----
    public void updateMicrochip(String microchipNumber) {
        this.microchipNumber = microchipNumber;
    }

    public void markDeceased() {
        this.status = PatientStatus.DECEASED;
    }
}
```

**Kural:** Entity'lerde public setter YOK. Değişiklik, anlamlı isimli davranış metotlarıyla yapılır (`markDeceased()`, `updateMicrochip()`) — bu, entity'nin her zaman geçerli bir durumda kalmasını garanti eder (SOLID'in "her sınıf kendi bütünlüğünü korur" ilkesi).

## 2. `domain/PatientStatus.java`, `domain/Sex.java` (Enum)

```java
package com.vetos.modules.patient.domain;

public enum PatientStatus { ACTIVE, DECEASED, TRANSFERRED }
public enum Sex { MALE, FEMALE, UNKNOWN }
```

## 3. `domain/PatientRepository.java` (Port — tam CRUD, sadece bu modül kullanır)

```java
package com.vetos.modules.patient.domain;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository {
    Patient save(Patient patient);
    Optional<Patient> findById(UUID id);
    java.util.List<Patient> findByOwnerId(UUID ownerId);
    java.util.List<Patient> searchByNameOrOwner(UUID tenantId, String query);
}
```

## 4. `domain/PatientLookupPort.java` (Port — sadece okuma, DİĞER modüller kullanır)

```java
package com.vetos.modules.patient.domain;

import java.util.UUID;

/**
 * Diger moduller (billing, encounter, appointment) hasta bilgisine
 * SADECE bu port uzerinden erisir. PatientRepository'yi ASLA import etmezler.
 */
public interface PatientLookupPort {
    PatientSummary findSummaryById(UUID patientId);
}
```

```java
package com.vetos.modules.patient.domain;

import java.util.UUID;

public record PatientSummary(UUID id, String name, UUID ownerId, String speciesName) {}
```

## 5. `application/RegisterPatientUseCase.java`

```java
package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.platform.event.DomainEventPublisher;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegisterPatientUseCase {

    private final PatientRepository patientRepository;
    private final DomainEventPublisher eventPublisher;

    public java.util.UUID execute(RegisterPatientCommand command) {
        Patient patient = Patient.register(
            command.tenantId(), command.ownerId(), command.speciesId(), command.name()
        );
        patientRepository.save(patient);
        eventPublisher.publish(new com.vetos.modules.patient.domain.event.PatientRegisteredEvent(patient.getId()));
        return patient.getId();
    }
}
```

```java
package com.vetos.modules.patient.application;

import java.util.UUID;

public record RegisterPatientCommand(UUID tenantId, UUID ownerId, UUID speciesId, String name) {}
```

**Kural:** Use-case sınıfı SADECE `domain` paketindeki port arayüzlerini bilir (`PatientRepository`). `infrastructure` paketinden hiçbir şey import etmez.

## 6. `infrastructure/persistence/PatientJpaRepository.java` (Spring Data)

```java
package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface PatientJpaRepository extends JpaRepository<Patient, UUID> {
    List<Patient> findByOwnerId(UUID ownerId);
    List<Patient> findByTenantIdAndNameContainingIgnoreCase(UUID tenantId, String name);
}
```

**Not:** Bu interface **package-private** (`interface`, `public` değil) — dışarıdan sadece adapter üzerinden erişilir.

## 7. `infrastructure/persistence/PatientRepositoryAdapter.java` (Port implementasyonu)

```java
package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PatientRepositoryAdapter implements PatientRepository {

    private final PatientJpaRepository jpaRepository;

    @Override
    public Patient save(Patient patient) { return jpaRepository.save(patient); }

    @Override
    public Optional<Patient> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Patient> findByOwnerId(UUID ownerId) { return jpaRepository.findByOwnerId(ownerId); }

    @Override
    public List<Patient> searchByNameOrOwner(UUID tenantId, String query) {
        return jpaRepository.findByTenantIdAndNameContainingIgnoreCase(tenantId, query);
    }
}
```

## 8. `infrastructure/PatientLookupAdapter.java` (Lookup port implementasyonu)

```java
package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PatientLookupAdapter implements PatientLookupPort {

    private final PatientJpaRepository jpaRepository;

    @Override
    public PatientSummary findSummaryById(UUID patientId) {
        Patient p = jpaRepository.findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
        return new PatientSummary(p.getId(), p.getName(), p.getOwnerId(), null);
    }
}
```

## 9. `domain/exception/PatientNotFoundException.java`

```java
package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class PatientNotFoundException extends DomainException {
    public PatientNotFoundException(UUID id) {
        super("PATIENT_NOT_FOUND", "Hasta bulunamadi: " + id);
    }
}
```

## 10. `api/PatientsController.java`

```java
package com.vetos.modules.patient.api;

import com.vetos.modules.patient.application.*;
import com.vetos.modules.patient.api.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientsController {

    private final RegisterPatientUseCase registerPatientUseCase;

    @PostMapping
    public ResponseEntity<PatientResponse> register(@RequestBody @jakarta.validation.Valid RegisterPatientRequest request) {
        UUID id = registerPatientUseCase.execute(
            new RegisterPatientCommand(request.tenantId(), request.ownerId(), request.speciesId(), request.name())
        );
        return ResponseEntity.status(201).body(new PatientResponse(id, request.name()));
    }
}
```

## 11. `api/dto/RegisterPatientRequest.java`, `PatientResponse.java`

```java
package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterPatientRequest(
    @NotNull UUID tenantId,
    @NotNull UUID ownerId,
    @NotNull UUID speciesId,
    @NotBlank String name
) {}

public record PatientResponse(UUID id, String name) {}
```

## 12. Diğer bir modülün bu modülü nasıl kullandığı örneği

`billing` modülü fatura oluştururken hasta adını göstermek istiyor — `PatientRepository` DEĞİL, `PatientLookupPort` kullanır:

```java
package com.vetos.modules.billing.application;

import com.vetos.modules.patient.domain.PatientLookupPort; // SADECE lookup port
import lombok.RequiredArgsConstructor;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class CreateInvoiceUseCase {
    private final PatientLookupPort patientLookupPort; // patient.domain.PatientRepository ASLA import edilmez
    // ...
}
```

## 13. Test örneği: `RegisterPatientUseCaseTest.java`

```java
package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.platform.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class RegisterPatientUseCaseTest {

    @Test
    void should_savePatientAndPublishEvent_when_validCommandGiven() {
        PatientRepository repository = Mockito.mock(PatientRepository.class);
        DomainEventPublisher publisher = Mockito.mock(DomainEventPublisher.class);
        Mockito.when(repository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterPatientUseCase useCase = new RegisterPatientUseCase(repository, publisher);
        UUID id = useCase.execute(new RegisterPatientCommand(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Rex"
        ));

        assertThat(id).isNotNull();
        Mockito.verify(publisher).publish(Mockito.any());
    }
}
```

---

**Yeni bir modül yazarken:** Bu 13 adımı aynı sırayla, aynı isimlendirmeyle uygula. Sadece entity alanları ve iş kuralları değişir — katman yapısı, isimlendirme deseni, port/adapter ayrımı BİREBİR AYNI kalır.
