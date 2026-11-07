package com.vetos.modules.consentform.domain;

import java.util.UUID;

/** AI modulunun sablon taslagi icin okudugu kaynak gorsel. */
public record FormSourceImageForAi(UUID id, String contentType, byte[] content) {}
