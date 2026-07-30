package com.vetos.modules.integration.tarbil.domain;

import java.util.UUID;

public record TarbilSyncRequest(UUID patientId, TarbilSyncType syncType, String payload) {}
