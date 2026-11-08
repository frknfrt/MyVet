package com.vetos.modules.consentform.infrastructure.adapter;

import com.vetos.modules.consentform.application.GetSignedFormUseCase;
import com.vetos.modules.consentform.application.dto.*;
import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.consentform.domain.exception.SignedFormNotFoundException;
import com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort.InvalidSourceException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignedFormWriteBackLookupAdapterTest {
    private final UUID id = UUID.randomUUID(), patient = UUID.randomUUID(), owner = UUID.randomUUID();
    private final GetSignedFormUseCase getForm = mock(GetSignedFormUseCase.class);
    private final SignedFormWriteBackLookupAdapter adapter = new SignedFormWriteBackLookupAdapter(getForm);

    private SignedFormDetail form(SignerType signer, SignedFormStatus status, boolean integrity) {
        var content = new FormTemplateContent("Onam", List.of(new FormSection(null, List.of(
            new FormItem(FormItemType.PHONE, "phone", "Telefon", null, false, "owner.phone", null, null, false),
            new FormItem(FormItemType.SHORT_TEXT, "color", "Renk", null, false, "patient.color", null, null, false),
            new FormItem(FormItemType.CHOICE, "choice", "E-posta", null, false, "owner.email", null, List.of("a", "b"), false)
        ))));
        return new SignedFormDetail(id, "Onam", content, Map.of("phone", " 5551112233 ", "color", "Sarı", "choice", List.of("a")),
            new SignerInput(signer, "İmzalayan", null, null), Instant.now(), "Dr", patient, owner, null, status, null, null, integrity);
    }

    @Test
    void ownerSignedValuesAreAllowedButChoiceIsExcluded() {
        when(getForm.execute(id)).thenReturn(form(SignerType.OWNER, SignedFormStatus.VALID, true));
        assertThat(adapter.allowedValues(id, patient, owner)).containsExactlyInAnyOrderEntriesOf(
            Map.of("owner.phone", Set.of("5551112233"), "patient.color", Set.of("Sarı")));
    }

    @Test
    void otherSignerCannotAuthorizeOwnerFields() {
        when(getForm.execute(id)).thenReturn(form(SignerType.OTHER, SignedFormStatus.VALID, true));
        assertThat(adapter.allowedValues(id, patient, owner)).containsExactlyEntriesOf(Map.of("patient.color", Set.of("Sarı")));
    }

    @Test
    void wrongPatientAndOwnerAreRejected() {
        when(getForm.execute(id)).thenReturn(form(SignerType.OWNER, SignedFormStatus.VALID, true));
        assertThatThrownBy(() -> adapter.allowedValues(id, UUID.randomUUID(), owner)).isInstanceOf(InvalidSourceException.class);
        assertThatThrownBy(() -> adapter.allowedValues(id, patient, UUID.randomUUID())).isInstanceOf(InvalidSourceException.class);
    }

    @Test
    void voidedOrTamperedFormsCannotAuthorizeUpdates() {
        when(getForm.execute(id)).thenReturn(form(SignerType.OWNER, SignedFormStatus.VOIDED, true));
        assertThatThrownBy(() -> adapter.allowedValues(id, patient, owner)).isInstanceOf(InvalidSourceException.class);
        when(getForm.execute(id)).thenReturn(form(SignerType.OWNER, SignedFormStatus.VALID, false));
        assertThatThrownBy(() -> adapter.allowedValues(id, patient, owner)).isInstanceOf(InvalidSourceException.class);
    }

    @Test
    void missingOrOtherTenantFormsAreNotFound() {
        when(getForm.execute(id)).thenThrow(new SignedFormNotFoundException(id));
        assertThatThrownBy(() -> adapter.allowedValues(id, patient, owner)).isInstanceOf(SignedFormNotFoundException.class);
        assertThatThrownBy(() -> adapter.allowedValues(null, patient, owner)).isInstanceOf(InvalidSourceException.class);
    }
}
