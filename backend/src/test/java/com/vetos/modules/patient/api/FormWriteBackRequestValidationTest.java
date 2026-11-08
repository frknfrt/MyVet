package com.vetos.modules.patient.api;

import com.vetos.modules.patient.application.ApplyFormWriteBacksUseCase;
import com.vetos.platform.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FormWriteBackRequestValidationTest {
    @Test
    void missingFormIdIsRejectedBeforeUseCase() throws Exception {
        var useCase = mock(ApplyFormWriteBacksUseCase.class);
        var mvc = MockMvcBuilders.standaloneSetup(new FormWriteBacksController(useCase))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = """
            {"ownerId":"%s","patientId":"%s","items":[{"binding":"owner.phone","value":"5551112233"}]}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());
        mvc.perform(post("/api/v1/form-write-backs").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }
}
