package com.vetos.platform.quota;

import com.vetos.platform.exception.DomainException;

/** GlobalExceptionHandler'da ozel handler: 429, Retry-After YOK (ay basinda acilir). */
public class MonthlyQuotaExceededException extends DomainException {

    public MonthlyQuotaExceededException(UsageKind kind, int used, int quota, int requested) {
        super("MONTHLY_QUOTA_EXCEEDED", messageFor(kind, used, quota, requested));
    }

    private static String messageFor(UsageKind kind, int used, int quota, int requested) {
        if (kind == UsageKind.AI) {
            return "Bu ayki AI hakkınız doldu (" + used + "/" + quota + "). Paketinizi yükseltmek için yöneticinize başvurun.";
        }
        int remaining = Math.max(0, quota - used);
        return "Bu ayki mesaj hakkınız yetersiz: " + requested + " alıcı, kalan " + remaining + ".";
    }
}
