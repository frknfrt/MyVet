package com.vetos.modules.ai.infrastructure.adapter;

import com.vetos.modules.consentform.domain.FormBinding;
import com.vetos.modules.consentform.domain.FormItemType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Prompt, icerik modeliyle (FormBinding/FormItemType) ayni kalmali. */
class FormTemplateExtractionPromptTest {

    @Test
    void should_listEveryBindingAndItemType() {
        for (FormBinding b : FormBinding.values()) {
            assertThat(FormTemplateExtractionPrompt.SYSTEM).contains(b.key());
        }
        for (FormItemType t : FormItemType.values()) {
            assertThat(FormTemplateExtractionPrompt.SYSTEM).contains(t.name());
        }
    }

    @Test
    void should_stripMarkdownFence() {
        assertThat(ClaudeVisionCompletion.stripMarkdownFence("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(ClaudeVisionCompletion.stripMarkdownFence(" {\"a\":1} ")).isEqualTo("{\"a\":1}");
    }
}
