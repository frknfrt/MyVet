package com.vetos.platform.branch;

import com.vetos.platform.exception.DomainException;

/** GlobalExceptionHandler'da ozel handler: 403 (paketin izin verdigi sube sayisi doldu). */
public class BranchLimitExceededException extends DomainException {

    public BranchLimitExceededException(int currentCount, int maxBranches) {
        super("BRANCH_LIMIT_EXCEEDED", messageFor(currentCount, maxBranches));
    }

    private static String messageFor(int currentCount, int maxBranches) {
        return "Paketinizin izin verdiği şube sayısına ulaştınız (" + currentCount + "/" + maxBranches
            + "). Daha fazla şube eklemek için paketinizi yükseltin.";
    }
}
