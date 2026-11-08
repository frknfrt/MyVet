package com.vetos;

import com.vetos.modules.ai.application.ExtractFormTemplateFromImageCommand;
import com.vetos.modules.ai.application.ExtractFormTemplateFromImageResult;
import com.vetos.modules.ai.application.ExtractFormTemplateFromImageUseCase;
import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.FormTemplateContentCodec;
import com.vetos.modules.consentform.domain.FormItem;
import com.vetos.modules.consentform.domain.FormItemType;
import com.vetos.modules.consentform.domain.FormSection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT/security chain; extraction is mocked so no external model is called. */
@SpringBootTest(properties = "vetly.rate-limit.tenant-ai-per-minute=1")
@AutoConfigureMockMvc
class FormTemplateDraftEndpointsSecurityTest {
    private static final String PATH = "/api/v1/ai/form-template-drafts";
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper mapper;
    @MockitoBean ExtractFormTemplateFromImageUseCase extract;

    private String body(UUID source) { return "{\"sourceImageId\":\"" + source + "\"}"; }

    @Test
    void vetIsForbiddenBeforeExtraction() throws Exception {
        String token = tokens.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), "VET");
        mvc.perform(post(PATH).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        verifyNoInteractions(extract);
    }

    @Test
    void anonymousRequestIsUnauthorizedBeforeExtraction() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(extract);
    }

    @Test
    void adminCanExtractUsingAuthenticatedTenantAndStaff() throws Exception {
        UUID tenant = UUID.randomUUID(), staff = UUID.randomUUID(), source = UUID.randomUUID(), job = UUID.randomUUID();
        when(extract.execute(any())).thenReturn(new ExtractFormTemplateFromImageResult(
            job, true, new FormTemplateContent("Taslak", List.of()), List.of()));
        String token = tokens.generateToken(staff, tenant, List.of(), "ADMIN");
        mvc.perform(post(PATH).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(source)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiJobId").value(job.toString()))
            .andExpect(jsonPath("$.modelConnected").value(true))
            .andExpect(jsonPath("$.content.title").value("Taslak"));
        var command = ArgumentCaptor.forClass(ExtractFormTemplateFromImageCommand.class);
        verify(extract).execute(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo(tenant);
        assertThat(command.getValue().requestedByStaffUserId()).isEqualTo(staff);
        assertThat(command.getValue().sourceImageId()).isEqualTo(source);
    }

    @Test
    void rateLimitIsSharedByStaffOfSameTenant_andRejectsBeforeExtraction() throws Exception {
        UUID tenant = UUID.randomUUID();
        String first = tokens.generateToken(UUID.randomUUID(), tenant, List.of(), "ADMIN");
        String secondStaff = tokens.generateToken(UUID.randomUUID(), tenant, List.of(), "ADMIN");
        String otherTenant = tokens.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), "ADMIN");
        String payload = body(UUID.randomUUID());
        when(extract.execute(any())).thenReturn(new ExtractFormTemplateFromImageResult(
            UUID.randomUUID(), true, new FormTemplateContent("Taslak", List.of()), List.of()));
        mvc.perform(post(PATH).header("Authorization", "Bearer " + first)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        mvc.perform(post(PATH).header("Authorization", "Bearer " + secondStaff)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
        verify(extract, times(1)).execute(any());
        mvc.perform(post(PATH).header("Authorization", "Bearer " + otherTenant)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        verify(extract, times(2)).execute(any());
    }

    @Test
    void draftResponsePreservesNullableFieldsAndFrontendItemShape() throws Exception {
        var content = new FormTemplateContent("Onam", List.of(new FormSection(null, List.of(
            new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, false, null, null, null, false),
            new FormItem(FormItemType.NUMBER, "kilo", "Kilo", null, true, "patient.weightKg", "kg", null, false),
            new FormItem(FormItemType.CHOICE, "tur", "Tür", null, false, "patient.species", null, List.of("Kedi", "Köpek"), true),
            new FormItem(FormItemType.TEXT_BLOCK, null, null, "Onay metni", false, null, null, null, false),
            new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false)
        ))));
        when(extract.execute(any())).thenReturn(new ExtractFormTemplateFromImageResult(
            UUID.randomUUID(), true, content, List.of("Kontrol edin")));
        String token = tokens.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), "ADMIN");
        var response = mvc.perform(post(PATH).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
            .andExpect(status().isOk()).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(json.get("content")).isEqualTo(mapper.readTree(FormTemplateContentCodec.toJson(content)));
        assertThat(json.get("modelConnected").booleanValue()).isTrue();
        assertThat(json.get("warnings").get(0).textValue()).isEqualTo("Kontrol edin");
        for (var field : json.get("content").get("sections").get(0).get("items")) {
            var names = new HashSet<String>();
            field.fieldNames().forEachRemaining(names::add);
            assertThat(names).containsExactlyInAnyOrder("type", "code", "label", "text", "required",
                "binding", "unit", "options", "multiple");
        }
        assertThat(json.get("content").get("sections").get(0).get("title").isNull()).isTrue();
        var text = json.get("content").get("sections").get(0).get("items").get(0);
        assertThat(text.get("options").isNull()).isTrue();
        assertThat(text.get("binding").isNull()).isTrue();
        assertThat(text.get("required").booleanValue()).isFalse();
        assertThat(text.get("multiple").booleanValue()).isFalse();
    }

    @Test
    void disconnectedFallbackPreservesEmptyTitleNullsAndSignature() throws Exception {
        var content = new FormTemplateContent("", List.of(new FormSection(null, List.of(
            new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false)))));
        when(extract.execute(any())).thenReturn(new ExtractFormTemplateFromImageResult(
            UUID.randomUUID(), false, content, List.of("Elle oluşturun")));
        String token = tokens.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), "ADMIN");
        var response = mvc.perform(post(PATH).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
            .andExpect(status().isOk()).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(json.get("modelConnected").booleanValue()).isFalse();
        assertThat(json.get("content")).isEqualTo(mapper.readTree(FormTemplateContentCodec.toJson(content)));
        assertThat(json.get("content").get("title").textValue()).isEmpty();
        var signature = json.get("content").get("sections").get(0).get("items").get(0);
        assertThat(signature.get("type").textValue()).isEqualTo("SIGNATURE");
        assertThat(signature.get("code").textValue()).isEqualTo("imza");
        assertThat(signature.get("required").booleanValue()).isTrue();
        assertThat(signature.get("text").isNull()).isTrue();
        assertThat(signature.get("multiple").booleanValue()).isFalse();
        assertThat(json.get("warnings").get(0).textValue()).isEqualTo("Elle oluşturun");
    }
}
