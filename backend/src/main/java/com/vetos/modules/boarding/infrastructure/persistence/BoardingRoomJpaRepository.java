package com.vetos.modules.boarding.infrastructure.persistence;

import com.vetos.modules.boarding.domain.BoardingRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BoardingRoomJpaRepository extends JpaRepository<BoardingRoom, UUID> {
    List<BoardingRoom> findByTenantId(UUID tenantId);
}
