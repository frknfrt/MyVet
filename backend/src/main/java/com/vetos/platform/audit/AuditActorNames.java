package com.vetos.platform.audit;

import java.util.UUID;

/** Personel adini yazma aninda okur (uygulamasi tenant modulunde). Bulunamazsa null. */
public interface AuditActorNames {

    String staffName(UUID tenantId, UUID staffUserId);
}
