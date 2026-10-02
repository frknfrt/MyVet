package com.vetos.modules.integration.tarbil.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Anahtar govdede: serbest metin asi adlari ("DHPPi/L") URL yolunda guvenle tasinamaz. */
public record LearnMappingRequest(@NotBlank @Size(max = 200) String key, @NotNull JsonNode fields) {}
