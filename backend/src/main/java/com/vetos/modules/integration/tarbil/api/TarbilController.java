package com.vetos.modules.integration.tarbil.api;

import com.vetos.modules.integration.tarbil.api.dto.TarbilStatusResponse;
import com.vetos.modules.integration.tarbil.api.dto.TarbilSyncLogResponse;
import com.vetos.modules.integration.tarbil.application.GetTarbilStatusSummaryUseCase;
import com.vetos.modules.integration.tarbil.application.ListTarbilSyncLogsUseCase;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** api-conventions.md rol matrisi: /tarbil/** -> ADMIN, VET (aktarimi hekimler yapar). */
@RestController
@RequestMapping("/api/v1/tarbil")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','VET')")
public class TarbilController {

    private final GetTarbilStatusSummaryUseCase getTarbilStatusSummaryUseCase;
    private final ListTarbilSyncLogsUseCase listTarbilSyncLogsUseCase;

    @GetMapping("/status")
    public TarbilStatusResponse status() {
        return TarbilStatusResponse.from(getTarbilStatusSummaryUseCase.execute(TenantContext.current()));
    }

    @GetMapping("/sync-logs")
    public List<TarbilSyncLogResponse> syncLogs() {
        return listTarbilSyncLogsUseCase.execute(TenantContext.current()).stream()
            .map(TarbilSyncLogResponse::from)
            .toList();
    }
}
