package com.vetos.modules.consentform.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vetos.modules.consentform.domain.exception.InvalidSignedFormValuesException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Imza anindaki alan degerlerinin dogrulanmasi ve deterministik JSON'a cevrilmesi (sablon sirasi). */
public final class SignedFormValues {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .disable(SerializationFeature.INDENT_OUTPUT)
        .serializationInclusion(JsonInclude.Include.ALWAYS)
        .build();

    private SignedFormValues() {}

    /** Degerleri dogrular; sablon sirasinda, String ya da List&lt;String&gt; iceren harita doner. */
    public static Map<String, Object> normalize(FormTemplateContent content, Map<String, Object> raw) {
        Map<String, Object> input = raw == null ? Map.of() : raw;
        Map<String, FormItem> fields = new LinkedHashMap<>();
        for (FormSection s : content.sections()) {
            for (FormItem item : s.items()) {
                if (item.type() != FormItemType.TEXT_BLOCK && item.type() != FormItemType.SIGNATURE) {
                    fields.put(item.code(), item);
                }
            }
        }
        for (String key : input.keySet()) {
            if (!fields.containsKey(key)) {
                throw new InvalidSignedFormValuesException("Formda tanımlı olmayan alan: " + key);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (FormItem item : fields.values()) {
            Object v = input.get(item.code());
            Object normalized = item.type() == FormItemType.CHOICE ? choice(item, v) : text(item, v);
            if (normalized != null) out.put(item.code(), normalized);
        }
        return out;
    }

    private static Object text(FormItem item, Object v) {
        if (v == null || (v instanceof String s && s.isBlank())) {
            if (item.required()) throw invalid(item, "zorunludur");
            return null;
        }
        if (!(v instanceof String s)) throw invalid(item, "metin olmalıdır");
        String t = s.strip();
        try {
            if (item.type() == FormItemType.DATE) LocalDate.parse(t);
            if (item.type() == FormItemType.NUMBER) new BigDecimal(t.replace(',', '.'));
        } catch (DateTimeParseException | NumberFormatException e) {
            throw invalid(item, item.type() == FormItemType.DATE ? "geçerli bir tarih olmalıdır" : "geçerli bir sayı olmalıdır");
        }
        return t;
    }

    private static Object choice(FormItem item, Object v) {
        List<String> picked = new ArrayList<>();
        if (v instanceof String s) {
            if (!s.isBlank()) picked.add(s);
        } else if (v instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof String s)) throw invalid(item, "seçenek metni olmalıdır");
                picked.add(s);
            }
        } else if (v != null) {
            throw invalid(item, "geçersiz değer");
        }
        if (picked.isEmpty()) {
            if (item.required()) throw invalid(item, "için en az bir seçenek seçilmelidir");
            return null;
        }
        if (!item.multiple() && picked.size() != 1) throw invalid(item, "için yalnız bir seçenek seçilebilir");
        for (String p : picked) {
            if (item.options() == null || !item.options().contains(p)) throw invalid(item, "için geçersiz seçenek: " + p);
        }
        return picked;
    }

    private static InvalidSignedFormValuesException invalid(FormItem item, String what) {
        return new InvalidSignedFormValuesException("\"" + item.label() + "\" alanı " + what);
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON üretilemedi", e);
        }
    }

    public static Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Form değerleri okunamadı", e);
        }
    }

    /** Ozet icin imzalayan bilgisinin deterministik JSON'u: {type, name, nationalId, phone}. */
    public static String signerJson(SignerType type, String name, String nationalId, String phone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type.name());
        m.put("name", name);
        m.put("nationalId", nationalId);
        m.put("phone", phone);
        return toJson(m);
    }


    /** Ozet icin kayit metadata JSON'u (sabit anahtar sirasi, null dahil); signedAt Instant.toString(). */
    public static String metadataJson(java.util.UUID templateId, java.util.UUID patientId, java.util.UUID ownerId,
                                      java.util.UUID encounterId, java.time.Instant signedAt, java.util.UUID signedBy,
                                      String clientIp, String userAgent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("templateId", templateId == null ? null : templateId.toString());
        m.put("patientId", patientId == null ? null : patientId.toString());
        m.put("ownerId", ownerId == null ? null : ownerId.toString());
        m.put("encounterId", encounterId == null ? null : encounterId.toString());
        m.put("signedAt", signedAt == null ? null : signedAt.toString());
        m.put("signedBy", signedBy == null ? null : signedBy.toString());
        m.put("clientIp", clientIp);
        m.put("userAgent", userAgent);
        return toJson(m);
    }
}
