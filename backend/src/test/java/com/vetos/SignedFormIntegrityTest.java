package com.vetos;

import com.vetos.modules.consentform.application.*;
import com.vetos.modules.consentform.application.dto.CreateSignedFormCommand;
import com.vetos.modules.consentform.application.dto.SignedFormDetail;
import com.vetos.modules.consentform.application.dto.SignerInput;
import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.consentform.domain.exception.SignedFormNotFoundException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientSummary;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffSummary;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.platform.storage.FileStoragePort;
import com.vetos.platform.storage.StoredFileNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Imzali form: sablon duzenlense de eski metin, deger kurcalansa integrityOk=false, kiracilar arasi 404. Gercek Postgres. */
@SpringBootTest
class SignedFormIntegrityTest extends TenantScopedTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 13, 10, 26, 10, 9, 8, 7};

    @Autowired CreateFormTemplateUseCase createFormTemplateUseCase;
    @Autowired UpdateFormTemplateUseCase updateFormTemplateUseCase;
    @Autowired CreateSignedFormUseCase createSignedFormUseCase;
    @Autowired GetSignedFormUseCase getSignedFormUseCase;
    @Autowired GetSignatureImageUseCase getSignatureImageUseCase;
    @Autowired ListSignedFormsUseCase listSignedFormsUseCase;
    @PersistenceContext EntityManager entityManager;

    @MockitoBean PatientLookupPort patientLookupPort;
    @MockitoBean OwnerLookupPort ownerLookupPort;
    @MockitoBean StaffUserLookupPort staffUserLookupPort;
    @MockitoBean FileStoragePort fileStoragePort;

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final List<UUID> tenants = new ArrayList<>();
    private final UUID staffId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        when(fileStoragePort.store(any(), anyString())).thenAnswer(i -> {
            String ref = UUID.randomUUID().toString();
            files.put(ref, i.getArgument(0));
            return ref;
        });
        when(fileStoragePort.retrieve(anyString())).thenAnswer(i -> {
            byte[] b = files.get(i.<String>getArgument(0));
            if (b == null) throw new StoredFileNotFoundException(i.getArgument(0));
            return b;
        });
        when(patientLookupPort.findSummaryById(patientId)).thenReturn(new PatientSummary(patientId, "Pamuk", ownerId, "Kedi"));
        when(ownerLookupPort.findSummaryById(ownerId))
            .thenReturn(new OwnerSummary(ownerId, "Ayşe Yılmaz", null, null, null, null, null, false, false));
        when(staffUserLookupPort.findSummaryById(staffId)).thenReturn(new StaffSummary(staffId, "Dr. Veli", StaffRole.VET, null));
    }

    @AfterEach
    void purge() {
        for (UUID t : tenants) {
            inRootSessionVoid(() -> {
                entityManager.createNativeQuery("DELETE FROM signed_forms WHERE tenant_id = :t").setParameter("t", t).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM form_templates WHERE tenant_id = :t").setParameter("t", t).executeUpdate();
            });
        }
        tenants.clear();
    }

    private static FormTemplateContent content(String title, String adLabel) {
        return new FormTemplateContent(title, List.of(new FormSection("Bölüm", List.of(
            new FormItem(FormItemType.SHORT_TEXT, "ad", adLabel, null, true, null, null, null, false),
            new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false)))));
    }

    private UUID createTemplate(UUID tenant, String title) {
        tenants.add(tenant);
        return asTenant(tenant, () -> createFormTemplateUseCase.execute(staffId, content(title, "Ad"), null));
    }

    private UUID sign(UUID tenant, UUID templateId) {
        return asTenant(tenant, () -> createSignedFormUseCase.execute(staffId, new CreateSignedFormCommand(
            templateId, patientId, null, Map.of("ad", "Pamuk"),
            new SignerInput(SignerType.OWNER, null, null, null), Base64.getEncoder().encodeToString(PNG), "127.0.0.1", "JUnit")));
    }

    @Test
    void should_renderFromSnapshot_after_templateEdited() {
        UUID tenant = UUID.randomUUID();
        UUID templateId = createTemplate(tenant, "Eski Başlık");
        UUID formId = sign(tenant, templateId);

        asTenantVoid(tenant, () -> updateFormTemplateUseCase.execute(staffId, templateId, content("Yeni Başlık", "Yeni Etiket")));

        SignedFormDetail d = asTenant(tenant, () -> getSignedFormUseCase.execute(formId));
        assertThat(d.content().title()).isEqualTo("Eski Başlık");
        assertThat(d.content().sections().get(0).items().get(0).label()).isEqualTo("Ad");
        assertThat(d.templateTitle()).isEqualTo("Eski Başlık");
        assertThat(d.values()).containsEntry("ad", "Pamuk");
        assertThat(d.signer().name()).isEqualTo("Ayşe Yılmaz");
        assertThat(d.signedByName()).isEqualTo("Dr. Veli");
        assertThat(d.ownerId()).isEqualTo(ownerId);
        assertThat(d.integrityOk()).isTrue();
        assertThat(asTenant(tenant, () -> getSignatureImageUseCase.execute(formId))).isEqualTo(PNG);
        assertThat(asTenant(tenant, () -> listSignedFormsUseCase.execute(patientId, null))).hasSize(1);
    }

    @Test
    void should_flagIntegrityFailure_when_storedValuesChanged() {
        UUID tenant = UUID.randomUUID();
        UUID formId = sign(tenant, createTemplate(tenant, "Form"));
        assertThat(asTenant(tenant, () -> getSignedFormUseCase.execute(formId)).integrityOk()).isTrue();

        inRootSessionVoid(() -> entityManager.createNativeQuery(
                "UPDATE signed_forms SET values_json = :v WHERE id = :id")
            .setParameter("v", "{\"ad\":\"Baskasi\"}").setParameter("id", formId).executeUpdate());

        assertThat(asTenant(tenant, () -> getSignedFormUseCase.execute(formId)).integrityOk()).isFalse();
    }

    @Test
    void should_flagIntegrityFailure_when_signatureMissingOrChanged() {
        UUID tenant = UUID.randomUUID();
        UUID formId = sign(tenant, createTemplate(tenant, "Form"));

        files.replaceAll((k, v) -> new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 1});
        assertThat(asTenant(tenant, () -> getSignedFormUseCase.execute(formId)).integrityOk()).isFalse();

        files.clear();
        assertThat(asTenant(tenant, () -> getSignedFormUseCase.execute(formId)).integrityOk()).isFalse();
    }

    private void tamper(UUID formId, String column, Object value) {
        inRootSessionVoid(() -> entityManager.createNativeQuery(
                "UPDATE signed_forms SET " + column + " = :v WHERE id = :id")
            .setParameter("v", value).setParameter("id", formId).executeUpdate());
    }

    @Test
    void should_flagIntegrityFailure_when_metadataOrSignerOrSnapshotChanged() {
        UUID tenant = UUID.randomUUID();
        UUID formId = sign(tenant, createTemplate(tenant, "Form"));
        assertThat(asTenant(tenant, () -> getSignedFormUseCase.execute(formId)).integrityOk()).isTrue();

        tamper(formId, "patient_id", UUID.randomUUID());
        assertThat(asTenant(tenant, () -> getSignedFormUseCase.execute(formId)).integrityOk()).isFalse();
    }

    @Test
    void should_flagIntegrityFailure_when_signerNameChanged() {
        UUID tenant = UUID.randomUUID();
        UUID formId = sign(tenant, createTemplate(tenant, "Form"));

        tamper(formId, "signer_name", "Baskasi");
        assertThat(asTenant(tenant, () -> getSignedFormUseCase.execute(formId)).integrityOk()).isFalse();
    }

    @Test
    void should_flagIntegrityFailure_when_snapshotChangedToValidJson() {
        UUID tenant = UUID.randomUUID();
        UUID formId = sign(tenant, createTemplate(tenant, "Form"));

        tamper(formId, "template_snapshot", FormTemplateContentCodec.toJson(content("Sahte", "Ad")));
        SignedFormDetail d = asTenant(tenant, () -> getSignedFormUseCase.execute(formId));
        assertThat(d.integrityOk()).isFalse();
        assertThat(d.content().title()).isEqualTo("Sahte");
    }

    @Test
    void should_notFail500_when_storedJsonIsCorrupted() {
        UUID tenant = UUID.randomUUID();
        UUID formId = sign(tenant, createTemplate(tenant, "Form"));

        tamper(formId, "template_snapshot", "{bozuk");
        tamper(formId, "values_json", "[not json");
        SignedFormDetail d = asTenant(tenant, () -> getSignedFormUseCase.execute(formId));
        assertThat(d.integrityOk()).isFalse();
        assertThat(d.content()).isNull();
        assertThat(d.values()).isNull();
    }

    @Test
    void get_should_return404_forOtherTenantsForm() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID formId = sign(tenantA, createTemplate(tenantA, "A Formu"));

        assertThatThrownBy(() -> asTenant(tenantB, () -> getSignedFormUseCase.execute(formId)))
            .isInstanceOf(SignedFormNotFoundException.class);
        assertThat(asTenant(tenantB, () -> listSignedFormsUseCase.execute(patientId, null))).isEmpty();
    }
}
