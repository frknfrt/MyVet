package com.vetos.modules.dataimport.application;

import java.time.Instant;
import java.util.Map;

/** Eski sistemden aktarilan bir kaydin klinikte gosterilen ek bilgileri. */
public record LegacyData(String sourceSystem, String sourceId, Instant importedAt, Map<String, String> data) {
}
