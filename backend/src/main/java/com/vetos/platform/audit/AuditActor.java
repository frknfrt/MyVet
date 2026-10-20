package com.vetos.platform.audit;

import java.util.UUID;

/** Islemi yapan. STAFF icin name null verilirse AuditPortAdapter AuditActorNames ile doldurur. */
public record AuditActor(ActorType type, UUID id, String name, String role, String impersonatedBy, Channel channel) {

    public enum ActorType { STAFF, PLATFORM_ADMIN }

    public enum Channel { WEB, EXTENSION }

    public static AuditActor staff(UUID id, String name, String role) {
        return new AuditActor(ActorType.STAFF, id, name, role, null, Channel.WEB);
    }

    public static AuditActor platformAdmin(UUID id, String email) {
        return new AuditActor(ActorType.PLATFORM_ADMIN, id, email, null, null, Channel.WEB);
    }
}
