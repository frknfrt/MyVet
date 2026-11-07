package com.vetos.modules.encounter.application.dto;

/** Hekimin alerji uyarisina yaniti: uyariyi gordu mu, eslesen ilac icin gerekcesi ne. */
public record AllergyAcknowledgement(boolean acknowledged, String overrideReason) {

    public static AllergyAcknowledgement of(Boolean acknowledged, String overrideReason) {
        return new AllergyAcknowledgement(Boolean.TRUE.equals(acknowledged), overrideReason);
    }
}
