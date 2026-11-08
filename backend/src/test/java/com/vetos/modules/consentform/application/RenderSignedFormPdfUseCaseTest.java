package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.consentform.domain.exception.SignedFormNotFoundException;
import com.vetos.modules.tenant.domain.*;
import com.vetos.platform.storage.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RenderSignedFormPdfUseCaseTest {
    private final SignedFormRepository forms = mock(SignedFormRepository.class);
    private final StaffUserLookupPort staff = mock(StaffUserLookupPort.class);
    private final FileStoragePort files = mock(FileStoragePort.class);
    private final TenantLookupPort tenants = mock(TenantLookupPort.class);
    private final ConsentFormPdfPort pdf = mock(ConsentFormPdfPort.class);
    private final RenderSignedFormPdfUseCase subject = new RenderSignedFormPdfUseCase(new GetSignedFormUseCase(forms, staff, files), tenants, pdf);
    private final UUID id = UUID.randomUUID(), tenant = UUID.randomUUID(), staffId = UUID.randomUUID();

    private void givenForm() {
        var form = SignedForm.create(tenant, UUID.randomUUID(), "Form", "{\"title\":\"Form\",\"sections\":[]}", "{}", "ref",
            UUID.randomUUID(), UUID.randomUUID(), null, SignerType.OWNER, "Sahip", null, null, staffId, Instant.now(), null, null, "digest");
        when(forms.findById(id)).thenReturn(Optional.of(form));
        when(staff.findSummaryById(staffId)).thenReturn(new StaffSummary(staffId, "Hekim", StaffRole.VET, null));
        when(tenants.findTenantName(tenant)).thenReturn(Optional.of("Klinik"));
        when(pdf.render(any())).thenReturn(new byte[]{1, 2});
    }

    @Test
    void readsFormAndSignatureOnceForEntirePdf() {
        givenForm();
        byte[] png = {1, 2, 3};
        when(files.retrieve("ref")).thenReturn(png);
        assertThat(subject.execute(id).content()).containsExactly(1, 2);
        verify(forms, times(1)).findById(id);
        verify(files, times(1)).retrieve("ref");
        var data = ArgumentCaptor.forClass(ConsentFormPdfData.class);
        verify(pdf).render(data.capture());
        assertThat(data.getValue().signaturePng()).containsExactly(png);
        assertThat(data.getValue().clinicName()).isEqualTo("Klinik");
    }

    @Test
    void missingSignatureStillRendersIntegrityWarningWithoutSecondRead() {
        givenForm();
        when(files.retrieve("ref")).thenThrow(new StoredFileNotFoundException("ref"));
        subject.execute(id);
        var data = ArgumentCaptor.forClass(ConsentFormPdfData.class);
        verify(pdf).render(data.capture());
        assertThat(data.getValue().signaturePng()).isNull();
        assertThat(data.getValue().integrityOk()).isFalse();
        verify(files, times(1)).retrieve("ref");
        verify(forms, times(1)).findById(id);
    }

    @Test
    void missingFormStopsBeforeRenderingOrReadingFiles() {
        when(forms.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> subject.execute(id)).isInstanceOf(SignedFormNotFoundException.class);
        verifyNoInteractions(files, staff, tenants, pdf);
    }
}
