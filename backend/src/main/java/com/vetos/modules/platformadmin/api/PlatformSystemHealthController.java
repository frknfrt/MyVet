package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.FailedEInvoiceResponse;
import com.vetos.modules.platformadmin.api.dto.FailedNotificationResponse;
import com.vetos.modules.platformadmin.api.dto.FailedTarbilSyncResponse;
import com.vetos.modules.platformadmin.application.ListPlatformSystemHealthUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform admin "Sistem Sagligi" paneli -- tum kiracilardaki basarisiz
 * bildirim, e-Fatura ve TARBIL senkron gonderimlerini goruntuler, manuel
 * tekrar deneme imkani sunar (bkz. SS101 kontrol listesi: operasyonel
 * gorunurluk).
 */
@RestController
@RequestMapping("/api/v1/platform-admin/system-health")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformSystemHealthController {

    private final ListPlatformSystemHealthUseCase listPlatformSystemHealthUseCase;

    @GetMapping("/notifications")
    public List<FailedNotificationResponse> failedNotifications() {
        return listPlatformSystemHealthUseCase.failedNotifications().stream()
            .map(FailedNotificationResponse::from)
            .toList();
    }

    @GetMapping("/efatura")
    public List<FailedEInvoiceResponse> failedEInvoices() {
        return listPlatformSystemHealthUseCase.failedEInvoices().stream()
            .map(FailedEInvoiceResponse::from)
            .toList();
    }

    @GetMapping("/tarbil")
    public List<FailedTarbilSyncResponse> failedTarbilSyncs() {
        return listPlatformSystemHealthUseCase.failedTarbilSyncs().stream()
            .map(FailedTarbilSyncResponse::from)
            .toList();
    }

    @PostMapping("/notifications/{id}/retry")
    public void retryNotification(@PathVariable UUID id) {
        listPlatformSystemHealthUseCase.retryNotification(id);
    }

    @PostMapping("/efatura/{id}/retry")
    public void retryEInvoice(@PathVariable UUID id) {
        listPlatformSystemHealthUseCase.retryEInvoice(id);
    }

    @PostMapping("/tarbil/{id}/retry")
    public void retryTarbilSync(@PathVariable UUID id) {
        listPlatformSystemHealthUseCase.retryTarbilSync(id);
    }
}
