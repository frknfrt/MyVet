package com.vetos.modules.tenant.infrastructure.adapter;

import com.vetos.modules.tenant.domain.InviteEmailPort;
import com.vetos.modules.tenant.domain.StaffInvite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gercek bir e-posta saglayicisi (SendGrid/SMTP) hesabi bu ortamda yok --
 * MockTarbilAdapter/MockEInvoiceGatewayAdapter ile ayni desen: cagiran kodu
 * (InviteStaffMemberUseCase) hic etkilemeden yerini alan bir stub. Davet
 * linki gercekten e-posta ile gitmez, sadece loglanir; Ayarlar > Kullanicilar
 * ekranindaki "Bekleyen Davetler" listesinden linki manuel kopyalayip
 * paylasmak simdilik gecerli is akisidir.
 */
@Component
@Slf4j
class MockInviteEmailAdapter implements InviteEmailPort {

    @Override
    public void sendInvite(StaffInvite invite, String tenantName, String acceptUrl) {
        log.info(
            "Davet e-postasi (mock): to={}, klinik={}, rol={}, link={}",
            invite.getEmail(), tenantName, invite.getRole(), acceptUrl
        );
    }
}
