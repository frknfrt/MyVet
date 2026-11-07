package com.vetos.modules.consentform.domain;

import java.util.UUID;

/** ai modulu consentform'un repository'sine degil bu porta baglanir. */
public interface FormSourceImageLookupPort {
    /** Yoksa (ya da baska kiracininsa) FormSourceImageNotFoundException firlatir. */
    FormSourceImageForAi find(UUID imageId);
}
