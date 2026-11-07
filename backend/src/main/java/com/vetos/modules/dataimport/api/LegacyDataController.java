package com.vetos.modules.dataimport.api;

import com.vetos.modules.dataimport.application.GetLegacyDataUseCase;
import com.vetos.modules.dataimport.application.LegacyData;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Klinik kullanicilari icin: aktarimla gelen "eski sistem bilgileri". */
@RestController
@RequestMapping("/api/v1/legacy-data")
@RequiredArgsConstructor
public class LegacyDataController {

    private final GetLegacyDataUseCase getLegacyDataUseCase;

    @GetMapping
    public ResponseEntity<LegacyData> get(@RequestParam ImportEntityType entityType, @RequestParam UUID id) {
        return getLegacyDataUseCase.execute(entityType, id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
