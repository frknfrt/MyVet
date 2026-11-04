package com.vetos.platform.staff;

import com.vetos.platform.exception.DomainException;

/** GlobalExceptionHandler'da ozel handler: 403 (paketin izin verdigi personel sayisi doldu). */
public class StaffUserLimitExceededException extends DomainException {

    public StaffUserLimitExceededException(int currentCount, int maxStaffUsers) {
        super("STAFF_USER_LIMIT_EXCEEDED", messageFor(currentCount, maxStaffUsers));
    }

    private static String messageFor(int currentCount, int maxStaffUsers) {
        return "Paketinizin izin verdiği personel sayısına ulaştınız (" + currentCount + "/" + maxStaffUsers
            + "). Daha fazla personel eklemek için paketinizi yükseltin.";
    }
}
