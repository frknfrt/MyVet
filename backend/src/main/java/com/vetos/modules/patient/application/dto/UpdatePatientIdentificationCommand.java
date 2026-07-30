package com.vetos.modules.patient.application.dto;

import java.util.UUID;

public record UpdatePatientIdentificationCommand(UUID patientId, String microchipNumber, String tarbilAnimalId) {}
