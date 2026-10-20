package com.vetos.platform.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingJsonMembersCustomizerTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void should_maskElevenDigitNumbers_inMessageMemberOnly() {
        JsonWriter<String> writer = JsonWriter.of(members -> {
            members.add("message", (String s) -> s);
            members.add("other", (String s) -> s);
            new PiiMaskingJsonMembersCustomizer().customize((JsonWriter.Members) members);
        });

        String json = writer.writeToString("TCKN 12345678901");

        assertThat(json).contains("\"message\":\"TCKN 123******01\"");
        assertThat(json).contains("\"other\":\"TCKN 12345678901\"");
    }
}
