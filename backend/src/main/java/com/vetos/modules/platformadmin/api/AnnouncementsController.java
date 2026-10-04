package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.AnnouncementResponse;
import com.vetos.modules.platformadmin.api.dto.SendAnnouncementRequest;
import com.vetos.modules.platformadmin.application.ListAnnouncementsUseCase;
import com.vetos.modules.platformadmin.application.SendAnnouncementUseCase;
import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform admin "Duyurular" paneli -- bkz. SendAnnouncementUseCase. */
@RestController
@RequestMapping("/api/v1/platform-admin/announcements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AnnouncementsController {

    private final SendAnnouncementUseCase sendAnnouncementUseCase;
    private final ListAnnouncementsUseCase listAnnouncementsUseCase;

    @GetMapping
    public List<AnnouncementResponse> list() {
        return listAnnouncementsUseCase.execute().stream().map(AnnouncementResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<Void> send(@RequestBody @Valid SendAnnouncementRequest request, @AuthenticationPrincipal AuthenticatedPlatformAdmin principal) {
        sendAnnouncementUseCase.execute(request.title(), request.body(), principal.platformAdminId(), principal.email());
        return ResponseEntity.status(201).build();
    }
}
