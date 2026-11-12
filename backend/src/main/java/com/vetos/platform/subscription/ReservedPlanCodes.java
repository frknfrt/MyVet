package com.vetos.platform.subscription;

/**
 * plans tablosunda satiri OLMAYAN, kodun ismen tanidigi plan kodlari.
 * TRIAL: sureli deneme (vitrinden ya da platform admin'den acilir).
 * INTERNAL: test ekibi hesabi -- suresiz, tum moduller acik, faturalama disi.
 */
public final class ReservedPlanCodes {

    public static final String TRIAL = "TRIAL";
    public static final String INTERNAL = "INTERNAL";

    private ReservedPlanCodes() {
    }

    public static boolean isReserved(String planCode) {
        return TRIAL.equals(planCode) || INTERNAL.equals(planCode);
    }
}
