package com.vetos.modules.boarding.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardingRoomRepository {
    BoardingRoom save(BoardingRoom room);
    Optional<BoardingRoom> findById(UUID id);
    List<BoardingRoom> findByTenantId(UUID tenantId);
}
