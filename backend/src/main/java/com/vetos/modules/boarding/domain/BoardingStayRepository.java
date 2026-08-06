package com.vetos.modules.boarding.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardingStayRepository {
    BoardingStay save(BoardingStay stay);
    Optional<BoardingStay> findById(UUID id);
    List<BoardingStay> findByTenantId(UUID tenantId);
    List<BoardingStay> findByRoomId(UUID roomId);
}
