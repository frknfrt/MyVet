package com.vetos.platform.quota;

import java.util.UUID;

/**
 * Paket kotalari (spec 2026-10-15 §2). Her islem kendi transaction'inda calisir (REQUIRES_NEW):
 * cagiran islem geri alinirsa sayim kendiliginden geri ALINMAZ -- gerekiyorsa release() cagir.
 */
public interface UsageQuotaPort {

    /** Kota yetiyorsa amount kadar ayirir; yetmiyorsa hicbir sey yazmaz ve MonthlyQuotaExceededException firlatir. */
    void reserve(UUID tenantId, UsageKind kind, int amount);

    /** Her zaman sayar, asla engellemez (otomatik mesajlar). */
    void recordUnconditional(UUID tenantId, UsageKind kind, int amount);

    /** Ayrilmis hakki iade eder (0'in altina inmez). */
    void release(UUID tenantId, UsageKind kind, int amount);

    /** Bu ayin kullanimi ve kotalari (null kota = sinirsiz). */
    UsageSnapshot currentUsage(UUID tenantId);
}
