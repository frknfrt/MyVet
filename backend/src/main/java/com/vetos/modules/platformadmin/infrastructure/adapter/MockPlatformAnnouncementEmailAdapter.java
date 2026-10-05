package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlatformAnnouncementEmailPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class MockPlatformAnnouncementEmailAdapter implements PlatformAnnouncementEmailPort {

    @Override
    public void sendAnnouncement(String tenantName, String recipientEmail, String title, String body) {
        log.info("Platform duyuru e-postasi (mock): to={}, klinik={}, baslik={}", recipientEmail, tenantName, title);
    }
}
