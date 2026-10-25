package com.vetos.modules.lab.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Diğer modüller (ai) lab sonucuna SADECE bu port üzerinden erişir. */
public interface LabResultLookupPort {
    /**
     * Yalnız COMPLETED; tarih (sonuç ?? istek) >= from; istek tarihi < requestedBefore (o güne kadar istenmiş, sonucu sonra gelmiş olabilir);
     * en yeniden eskiye, en fazla limit kayıt.
     */
    List<LabResultHistoryEntry> findCompletedBetween(UUID patientId, Instant from, Instant requestedBefore, int limit);
}
