package com.vetos.modules.notification.api;

import com.vetos.modules.notification.api.dto.NotificationLogResponse;
import com.vetos.modules.notification.api.dto.NotificationStatusResponse;
import com.vetos.modules.notification.application.GetNotificationStatusSummaryUseCase;
import com.vetos.modules.notification.application.ListNotificationLogsUseCase;
import com.vetos.modules.notification.application.RetryNotificationUseCase;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** api-conventions.md rol matrisi: /notifications/** -> sadece ADMIN. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NotificationsController {

    private final GetNotificationStatusSummaryUseCase getNotificationStatusSummaryUseCase;
    private final ListNotificationLogsUseCase listNotificationLogsUseCase;
    private final RetryNotificationUseCase retryNotificationUseCase;

    @GetMapping("/status")
    public NotificationStatusResponse status() {
        return NotificationStatusResponse.from(getNotificationStatusSummaryUseCase.execute(TenantContext.current()));
    }

    @GetMapping("/logs")
    public List<NotificationLogResponse> logs() {
        return listNotificationLogsUseCase.execute(TenantContext.current()).stream()
            .map(NotificationLogResponse::from)
            .toList();
    }

    @PostMapping("/logs/{id}/retry")
    public void retry(@PathVariable UUID id) {
        retryNotificationUseCase.execute(id);
    }
}
