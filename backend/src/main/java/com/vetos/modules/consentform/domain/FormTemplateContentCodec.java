package com.vetos.modules.consentform.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vetos.modules.consentform.domain.exception.InvalidFormTemplateException;

/**
 * Sablon icerigi <-> JSON. Cikti deterministiktir (girintisiz, null'lar dahil, record bildirim sirasi);
 * imza ozeti (SHA-256) buna dayanir.
 */
public final class FormTemplateContentCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .disable(SerializationFeature.INDENT_OUTPUT)
        .serializationInclusion(JsonInclude.Include.ALWAYS)
        .build();

    private FormTemplateContentCodec() {}

    public static String toJson(FormTemplateContent content) {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new InvalidFormTemplateException("Form içeriği JSON'a çevrilemedi", e);
        }
    }

    public static FormTemplateContent fromJson(String json) {
        try {
            FormTemplateContent c = MAPPER.readValue(json, FormTemplateContent.class);
            if (c == null) throw new InvalidFormTemplateException("Form içeriği boş");
            return c;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidFormTemplateException("Form içeriği okunamadı", e);
        }
    }
}
