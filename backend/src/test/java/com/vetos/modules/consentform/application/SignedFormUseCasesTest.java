package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.CreateSignedFormCommand;
import com.vetos.modules.consentform.application.dto.SignerInput;
import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.consentform.domain.exception.*;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientSummary;
import com.vetos.platform.storage.FileStoragePort;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignedFormUseCasesTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 13, 10, 26, 10, 1, 2, 3};

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Mock SignedFormSubmissionRepository submissions;
    @Mock FormTemplateRepository templateRepository;
    @Mock SignedFormRepository signedFormRepository;
    @Mock PatientLookupPort patientLookupPort;
    @Mock OwnerLookupPort ownerLookupPort;
    @Mock FileStoragePort fileStoragePort;
    @Mock com.vetos.modules.encounter.domain.EncounterLookupPort encounterLookupPort;

    private FormTemplate template;
    private CreateSignedFormUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        FormTemplateContent content = new FormTemplateContent("Anestezi Onamı", List.of(new FormSection("Bölüm", List.of(
            new FormItem(FormItemType.TEXT_BLOCK, null, null, "Onay metni A", false, null, null, null, false),
            new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, true, null, null, null, false),
            new FormItem(FormItemType.CHOICE, "tur", "Tür", null, true, null, null, List.of("Evet", "Hayır"), false),
            new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false)
        ))));
        template = FormTemplate.create(tenantId, "Anestezi Onamı", FormTemplateContentCodec.toJson(content), null, staffId);
        when(templateRepository.findById(any())).thenReturn(Optional.of(template));
        when(patientLookupPort.findSummaryById(patientId)).thenReturn(new PatientSummary(patientId, "Pamuk", ownerId, "Kedi"));
        when(ownerLookupPort.findSummaryById(ownerId))
            .thenReturn(new OwnerSummary(ownerId, "Ayşe Yılmaz", null, null, null, null, null, false, false));
        when(fileStoragePort.store(any(), any())).thenReturn("ref-1");
        when(signedFormRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        useCase = new CreateSignedFormUseCase(submissions, templateRepository, signedFormRepository, patientLookupPort,
            ownerLookupPort, fileStoragePort, encounterLookupPort);
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    private CreateSignedFormCommand command(Map<String, Object> values, SignerInput signer, String sigBase64) {
        return new CreateSignedFormCommand(UUID.randomUUID(), patientId, null, values, signer, sigBase64, "10.0.0.5", "UA/1.0", template.contentHash(), UUID.randomUUID());
    }

    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }

    private static final Map<String, Object> OK_VALUES = Map.of("ad", "Pamuk", "tur", List.of("Evet"));
    private static final SignerInput OWNER_SIGNER = new SignerInput(SignerType.OWNER, "Istemci Adi", null, null);

    private SignedForm savedForm() {
        ArgumentCaptor<SignedForm> captor = ArgumentCaptor.forClass(SignedForm.class);
        verify(signedFormRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_rejectTextOnlyTemplateEditBeforeSigning() {
        var opened = command(OK_VALUES, OWNER_SIGNER, b64(PNG));
        template.update(template.getTitle(), template.getContent().replace("Onay metni A", "Onay metni B"), staffId);

        assertThatThrownBy(() -> useCase.execute(staffId, opened))
            .isInstanceOf(FormTemplateChangedConflictException.class)
            .extracting("errorCode").isEqualTo("FORM_TEMPLATE_CHANGED");
        verifyNoInteractions(fileStoragePort, signedFormRepository, patientLookupPort);
    }

    @Test
    void should_rejectMissingTemplateHashWithoutSaving() {
        var cmd = new CreateSignedFormCommand(UUID.randomUUID(), patientId, null, OK_VALUES,
            OWNER_SIGNER, b64(PNG), null, null, null, UUID.randomUUID());
        assertThatThrownBy(() -> useCase.execute(staffId, cmd)).isInstanceOf(FormTemplateChangedConflictException.class);
        verifyNoInteractions(fileStoragePort, signedFormRepository);
    }

    @Test
    void should_acceptNewlyReadTemplateAfterEdit() {
        template.update(template.getTitle(), template.getContent().replace("Onay metni A", "Onay metni B"), staffId);
        useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(PNG)));
        assertThat(savedForm().getTemplateSnapshot()).contains("Onay metni B");
    }

    @Test
    void should_storeSnapshotValuesSignatureAndDigest() {
        useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(PNG)));

        SignedForm f = savedForm();
        assertThat(f.getTemplateSnapshot()).isEqualTo(template.getContent());
        assertThat(f.getValuesJson()).isEqualTo("{\"ad\":\"Pamuk\",\"tur\":[\"Evet\"]}");
        assertThat(f.getSignatureStorageRef()).isEqualTo("ref-1");
        verify(fileStoragePort).store(eq(PNG), eq("image/png"));
        String signerJson = SignedFormValues.signerJson(SignerType.OWNER, "Ayşe Yılmaz", null, null);
        String metadataJson = SignedFormValues.metadataJson(f.getTemplateId(), f.getPatientId(), f.getOwnerId(),
            f.getEncounterId(), f.getSignedAt(), f.getSignedBy(), f.getClientIp(), f.getUserAgent());
        assertThat(f.getContentDigest()).isEqualTo(
            SignedFormDigest.compute(template.getContent(), f.getValuesJson(), signerJson, metadataJson, PNG))
            .matches("[0-9a-f]{64}");
        assertThat(f.getSignedAt()).isEqualTo(f.getSignedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(f.getClientIp()).isEqualTo("10.0.0.5");
        assertThat(f.getUserAgent()).isEqualTo("UA/1.0");
        assertThat(f.getSignedBy()).isEqualTo(staffId);
        assertThat(f.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void should_takeOwnerFromPatient_notFromClient() {
        useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(PNG)));

        SignedForm f = savedForm();
        assertThat(f.getOwnerId()).isEqualTo(ownerId);
        assertThat(f.getSignerName()).isEqualTo("Ayşe Yılmaz");
    }

    @Test
    void should_writeValuesInTemplateOrder_notClientOrder() {
        Map<String, Object> reversed = new java.util.LinkedHashMap<>();
        reversed.put("tur", "Hayır");
        reversed.put("ad", "Pamuk");

        useCase.execute(staffId, command(reversed, OWNER_SIGNER, b64(PNG)));

        assertThat(savedForm().getValuesJson()).isEqualTo("{\"ad\":\"Pamuk\",\"tur\":[\"Hayır\"]}");
    }

    @Test
    void should_truncateUserAgentTo500() {
        CreateSignedFormCommand c = new CreateSignedFormCommand(UUID.randomUUID(), patientId, null, OK_VALUES,
            OWNER_SIGNER, b64(PNG), "1.1.1.1", "x".repeat(900), template.contentHash(), UUID.randomUUID());

        useCase.execute(staffId, c);

        assertThat(savedForm().getUserAgent()).hasSize(500);
    }

    @Test
    void should_reject_missingRequiredField() {
        assertThatThrownBy(() -> useCase.execute(staffId, command(Map.of("tur", List.of("Evet")), OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class).hasMessageContaining("Ad");
        assertThatThrownBy(() -> useCase.execute(staffId, command(Map.of("ad", "Pamuk"), OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class).hasMessageContaining("Tür");
        verify(signedFormRepository, never()).save(any());
    }

    @Test
    void should_reject_unknownCodeAndBadChoice() {
        assertThatThrownBy(() -> useCase.execute(staffId,
            command(Map.of("ad", "Pamuk", "tur", List.of("Evet"), "yok", "x"), OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class);
        assertThatThrownBy(() -> useCase.execute(staffId,
            command(Map.of("ad", "Pamuk", "tur", List.of("Belki")), OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class);
        assertThatThrownBy(() -> useCase.execute(staffId,
            command(Map.of("ad", "Pamuk", "tur", List.of("Evet", "Hayır")), OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class);
    }

    @Test
    void should_reject_nonPngSignature() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(jpeg))))
            .isInstanceOf(InvalidSignatureException.class);
        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, "%%%not-base64%%%")))
            .isInstanceOf(InvalidSignatureException.class);
        byte[] big = new byte[200 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(big))))
            .isInstanceOf(InvalidSignatureException.class);
        verify(fileStoragePort, never()).store(any(), any());
        verify(signedFormRepository, never()).save(any());
    }

    @Test
    void should_reject_archivedTemplate() {
        template.archive(staffId);

        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(FormTemplateArchivedException.class);
        verify(signedFormRepository, never()).save(any());
    }

    @Test
    void should_require_name_for_otherSigner() {
        assertThatThrownBy(() -> useCase.execute(staffId,
            command(OK_VALUES, new SignerInput(SignerType.OTHER, "  ", null, null), b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class);
        assertThatThrownBy(() -> useCase.execute(staffId,
            command(OK_VALUES, new SignerInput(SignerType.OTHER, "Veli", "123", null), b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class);

        useCase.execute(staffId, command(OK_VALUES, new SignerInput(SignerType.OTHER, "Veli Kaya", "12345678901", "555"), b64(PNG)));
        SignedForm f = savedForm();
        assertThat(f.getSignerName()).isEqualTo("Veli Kaya");
        assertThat(f.getSignerType()).isEqualTo(SignerType.OTHER);
        assertThat(f.getSignerNationalId()).isEqualTo("12345678901");
        verify(ownerLookupPort, never()).findSummaryById(any());
    }

    @Test
    void void_should_requireReason_and_rejectTwice() {
        SignedForm f = SignedForm.create(tenantId, UUID.randomUUID(), "T", "{}", "{}", "r", patientId, ownerId, null,
            SignerType.OWNER, "A", null, null, staffId, java.time.Instant.now(), null, null, "d");
        UUID id = UUID.randomUUID();
        when(signedFormRepository.findById(id)).thenReturn(Optional.of(f));
        VoidSignedFormUseCase voidUseCase = new VoidSignedFormUseCase(signedFormRepository);

        assertThatThrownBy(() -> voidUseCase.execute(staffId, id, " ")).isInstanceOf(InvalidSignedFormValuesException.class);
        assertThat(f.getStatus()).isEqualTo(SignedFormStatus.VALID);

        voidUseCase.execute(staffId, id, "Yanlış hasta");
        assertThat(f.getStatus()).isEqualTo(SignedFormStatus.VOIDED);
        assertThat(f.getVoidReason()).isEqualTo("Yanlış hasta");
        assertThat(f.getVoidedBy()).isEqualTo(staffId);

        assertThatThrownBy(() -> voidUseCase.execute(staffId, id, "Tekrar"))
            .isInstanceOf(SignedFormAlreadyVoidedException.class);
    }

    @Test
    void should_reject_encounterOfOtherPatient_orMissing() {
        UUID enc = UUID.randomUUID();
        when(encounterLookupPort.findSummaryById(enc)).thenReturn(new com.vetos.modules.encounter.domain.EncounterSummary(
            enc, UUID.randomUUID(), staffId, null));
        CreateSignedFormCommand other = new CreateSignedFormCommand(UUID.randomUUID(), patientId, enc, OK_VALUES,
            OWNER_SIGNER, b64(PNG), null, null, template.contentHash(), UUID.randomUUID());
        assertThatThrownBy(() -> useCase.execute(staffId, other)).isInstanceOf(InvalidEncounterException.class);

        UUID missing = UUID.randomUUID();
        when(encounterLookupPort.findSummaryById(missing))
            .thenThrow(new com.vetos.modules.encounter.domain.exception.EncounterNotFoundException(missing));
        CreateSignedFormCommand gone = new CreateSignedFormCommand(UUID.randomUUID(), patientId, missing, OK_VALUES,
            OWNER_SIGNER, b64(PNG), null, null, template.contentHash(), UUID.randomUUID());
        assertThatThrownBy(() -> useCase.execute(staffId, gone)).isInstanceOf(InvalidEncounterException.class)
            .extracting("errorCode").isEqualTo("INVALID_ENCOUNTER");
        verify(signedFormRepository, never()).save(any());
    }

    @Test
    void should_accept_encounterOfSamePatient() {
        UUID enc = UUID.randomUUID();
        when(encounterLookupPort.findSummaryById(enc)).thenReturn(new com.vetos.modules.encounter.domain.EncounterSummary(
            enc, patientId, staffId, null));

        useCase.execute(staffId, new CreateSignedFormCommand(UUID.randomUUID(), patientId, enc, OK_VALUES,
            OWNER_SIGNER, b64(PNG), null, null, template.contentHash(), UUID.randomUUID()));

        assertThat(savedForm().getEncounterId()).isEqualTo(enc);
    }

    @Test
    void list_should_requireFilter() {
        assertThatThrownBy(() -> new ListSignedFormsUseCase(signedFormRepository).execute(null, null))
            .isInstanceOf(InvalidSignedFormValuesException.class);
    }

    @Test
    void completedRequestReturnsOriginalIdWithoutSavingAgain() {
        var cmd = command(OK_VALUES, OWNER_SIGNER, b64(PNG));
        UUID original = UUID.randomUUID();
        when(submissions.claim(eq(tenantId), eq(cmd.requestId()), anyString())).thenReturn(Optional.of(original));
        assertThat(useCase.execute(staffId, cmd)).isEqualTo(original);
        verifyNoInteractions(templateRepository, patientLookupPort, ownerLookupPort, signedFormRepository, fileStoragePort);
    }

    @Test
    void missingRequestIdCannotCreateAForm() {
        var cmd = new CreateSignedFormCommand(UUID.randomUUID(), patientId, null, OK_VALUES,
            OWNER_SIGNER, b64(PNG), null, null, template.contentHash(), null);
        assertThatThrownBy(() -> useCase.execute(staffId, cmd)).isInstanceOf(InvalidSignedFormValuesException.class);
        verifyNoInteractions(submissions, fileStoragePort, signedFormRepository);
    }

    @Test
    void oversizedMalformedBase64IsRejectedBeforeDecodingOrReserving() {
        String oversized = "*".repeat(4 * CreateSignedFormUseCase.MAX_SIGNATURE_BYTES);
        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, oversized)))
            .isInstanceOf(InvalidSignatureException.class).hasMessageContaining("çok büyük");
        verifyNoInteractions(submissions, fileStoragePort, signedFormRepository);
    }

    @Test
    void decodedSizeIsCheckedWhenOverLimitImageFitsTheSameBase64Block() {
        byte[] max = new byte[CreateSignedFormUseCase.MAX_SIGNATURE_BYTES];
        byte[] over = new byte[max.length + 1];
        System.arraycopy(PNG, 0, over, 0, PNG.length);
        assertThat(b64(over).length()).isEqualTo(b64(max).length());
        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(over))))
            .isInstanceOf(InvalidSignatureException.class).hasMessageContaining("çok büyük");
        verifyNoInteractions(submissions, fileStoragePort, signedFormRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void exactLimitSignatureIsAcceptedWithOrWithoutPadding(boolean padded) {
        byte[] max = new byte[CreateSignedFormUseCase.MAX_SIGNATURE_BYTES];
        System.arraycopy(PNG, 0, max, 0, PNG.length);
        String encoded = padded ? b64(max) : b64(max).replaceAll("=+$", "");
        useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, " \n" + encoded + "\t "));
        verify(fileStoragePort).store(eq(max), eq("image/png"));
        verify(signedFormRepository).save(any());
    }

    @Test
    void legacyTemplateWithSignatureReferenceCannotCreateNewSignedForm() {
        template.update(template.getTitle(), template.getContent().replace("Onay metni A", "Onay {{ imza }}"), staffId);
        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(InvalidFormTemplateException.class).hasMessageContaining("{{imza}}");
        verifyNoInteractions(fileStoragePort, signedFormRepository);
    }

    private void enableMultipleChoice() {
        var content = new FormTemplateContent("Form", List.of(new FormSection(null, List.of(
            new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, true, null, null, null, false),
            new FormItem(FormItemType.CHOICE, "tur", "Tercih", null, true, null, null, List.of("Evet", "Hayır"), true),
            new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false)))));
        template.update("Form", FormTemplateContentCodec.toJson(content), staffId);
    }

    @Test
    void repeatedSelectionCannotCreateSignedForm() {
        enableMultipleChoice();
        assertThatThrownBy(() -> useCase.execute(staffId, command(
            Map.of("ad", "Pamuk", "tur", List.of("Evet", "Evet")), OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class).hasMessageContaining("tekrarlanamaz");
        verifyNoInteractions(fileStoragePort, signedFormRepository);
    }

    @Test
    void distinctMultipleSelectionsKeepTheirOrder() {
        enableMultipleChoice();
        useCase.execute(staffId, command(Map.of("ad", "Pamuk", "tur", List.of("Hayır", "Evet")), OWNER_SIGNER, b64(PNG)));
        assertThat(SignedFormValues.fromJson(savedForm().getValuesJson()).get("tur")).isEqualTo(List.of("Hayır", "Evet"));
    }

    @Test
    void legacyTemplateWithIncompatibleBindingCannotCreateNewSignedForm() {
        var content = new FormTemplateContent("Form", List.of(new FormSection(null, List.of(
            new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, true, "patient.weightKg", null, null, false),
            new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false)))));
        template.update("Form", FormTemplateContentCodec.toJson(content), staffId);
        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, OWNER_SIGNER, b64(PNG))))
            .isInstanceOf(InvalidFormTemplateException.class).hasMessageContaining("alan türüyle uyumsuz");
        verifyNoInteractions(fileStoragePort, signedFormRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "1234567890", "123456789012", "1234567890a", "123 5678901", "١٢٣٤٥٦٧٨٩٠١"
    })
    void ownerNationalIdMustBeElevenAsciiDigitsWhenProvided(String nationalId) {
        var signer = new SignerInput(SignerType.OWNER, "İstemci adı", nationalId, null);
        assertThatThrownBy(() -> useCase.execute(staffId, command(OK_VALUES, signer, b64(PNG))))
            .isInstanceOf(InvalidSignedFormValuesException.class)
            .hasMessageContaining("11 haneli rakam");
        verifyNoInteractions(fileStoragePort, signedFormRepository);
        verify(submissions, never()).complete(any(), any(), any());
    }

    @Test
    void ownerNationalIdIsTrimmedAndIncludedInDigest_whileNameComesFromOwnerRecord() {
        var signer = new SignerInput(SignerType.OWNER, "İstemci adı", " 12345678901 ", null);
        useCase.execute(staffId, command(OK_VALUES, signer, b64(PNG)));
        var form = savedForm();
        assertThat(form.getSignerNationalId()).isEqualTo("12345678901");
        assertThat(form.getSignerName()).isEqualTo("Ayşe Yılmaz");
        String signerJson = SignedFormValues.signerJson(SignerType.OWNER, "Ayşe Yılmaz", "12345678901", null);
        String metadata = SignedFormValues.metadataJson(form.getTemplateId(), form.getPatientId(), form.getOwnerId(),
            form.getEncounterId(), form.getSignedAt(), form.getSignedBy(), form.getClientIp(), form.getUserAgent());
        assertThat(form.getContentDigest()).isEqualTo(SignedFormDigest.compute(
            form.getTemplateSnapshot(), form.getValuesJson(), signerJson, metadata, PNG));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"   "})
    void ownerNationalIdRemainsOptional(String nationalId) {
        var signer = new SignerInput(SignerType.OWNER, null, nationalId, null);
        useCase.execute(staffId, command(OK_VALUES, signer, b64(PNG)));
        assertThat(savedForm().getSignerNationalId()).isNull();
    }

    @Test
    void list_should_useBothFiltersTogether() {
        when(signedFormRepository.findByPatientIdAndOwnerIdOrderBySignedAtDesc(patientId, ownerId)).thenReturn(List.of());
        assertThat(new ListSignedFormsUseCase(signedFormRepository).execute(patientId, ownerId)).isEmpty();
        verify(signedFormRepository).findByPatientIdAndOwnerIdOrderBySignedAtDesc(patientId, ownerId);
        verifyNoMoreInteractions(signedFormRepository);
    }

    @Test
    void list_should_preservePatientOnlyFilter() {
        when(signedFormRepository.findByPatientIdOrderBySignedAtDesc(patientId)).thenReturn(List.of());
        assertThat(new ListSignedFormsUseCase(signedFormRepository).execute(patientId, null)).isEmpty();
        verify(signedFormRepository).findByPatientIdOrderBySignedAtDesc(patientId);
        verifyNoMoreInteractions(signedFormRepository);
    }

    @Test
    void list_should_preserveOwnerOnlyFilter() {
        when(signedFormRepository.findByOwnerIdOrderBySignedAtDesc(ownerId)).thenReturn(List.of());
        assertThat(new ListSignedFormsUseCase(signedFormRepository).execute(null, ownerId)).isEmpty();
        verify(signedFormRepository).findByOwnerIdOrderBySignedAtDesc(ownerId);
        verifyNoMoreInteractions(signedFormRepository);
    }
}
