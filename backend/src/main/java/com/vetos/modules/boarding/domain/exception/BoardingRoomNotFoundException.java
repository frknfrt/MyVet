package com.vetos.modules.boarding.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class BoardingRoomNotFoundException extends DomainException {
    public BoardingRoomNotFoundException(UUID id) {
        super("BOARDING_ROOM_NOT_FOUND", "Konaklama odasi bulunamadi: " + id);
    }
}
