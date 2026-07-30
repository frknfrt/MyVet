package com.vetos.modules.encounter.domain;

import com.vetos.modules.encounter.domain.exception.EncounterInvalidTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "encounters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "encounter_date", nullable = false)
    private Instant encounterDate;

    @Column(columnDefinition = "text")
    private String subjective;

    @Column(columnDefinition = "text")
    private String objective;

    @Column(columnDefinition = "text")
    private String assessment;

    @Column(columnDefinition = "text")
    private String plan;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "template_used")
    private String templateUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EncounterStatus status;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    public static Encounter start(UUID patientId, UUID staffUserId, UUID appointmentId, String templateUsed) {
        Encounter encounter = new Encounter();
        encounter.patientId = patientId;
        encounter.staffUserId = staffUserId;
        encounter.appointmentId = appointmentId;
        encounter.templateUsed = templateUsed;
        encounter.encounterDate = Instant.now();
        encounter.status = EncounterStatus.DRAFT;
        encounter.aiGenerated = false;
        return encounter;
    }

    public void updateSoap(String subjective, String objective, String assessment, String plan) {
        this.subjective = subjective;
        this.objective = objective;
        this.assessment = assessment;
        this.plan = plan;
    }

    public void recordVitals(BigDecimal weightKg, BigDecimal temperatureC, Integer heartRate, Integer respiratoryRate) {
        this.weightKg = weightKg;
        this.temperatureC = temperatureC;
        this.heartRate = heartRate;
        this.respiratoryRate = respiratoryRate;
    }

    public void markAiGenerated() {
        this.aiGenerated = true;
    }

    public void finalizeEncounter() {
        if (status != EncounterStatus.DRAFT) {
            throw new EncounterInvalidTransitionException(status, EncounterStatus.FINALIZED);
        }
        this.status = EncounterStatus.FINALIZED;
        this.finalizedAt = Instant.now();
    }

    public void amend(String subjective, String objective, String assessment, String plan) {
        if (status != EncounterStatus.FINALIZED && status != EncounterStatus.AMENDED) {
            throw new EncounterInvalidTransitionException(status, EncounterStatus.AMENDED);
        }
        updateSoap(subjective, objective, assessment, plan);
        this.status = EncounterStatus.AMENDED;
    }
}
