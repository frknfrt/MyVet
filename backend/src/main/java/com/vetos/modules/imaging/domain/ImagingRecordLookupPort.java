package com.vetos.modules.imaging.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Diğer modüller (ai) görüntüleme kaydına SADECE bu port üzerinden erişir. */
public interface ImagingRecordLookupPort {
    /** Lab ile aynı kural: COMPLETED; tarih (sonuç ?? istek) >= from; istek < requestedBefore; en yeniden eskiye, en fazla limit. */
    List<ImagingHistoryEntry> findCompletedBetween(UUID patientId, Instant from, Instant requestedBefore, int limit);
}
