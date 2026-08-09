package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "monthly_price", nullable = false)
    private BigDecimal monthlyPrice;

    @Column(nullable = false)
    private boolean active;

    public static Plan create(String code, String name, BigDecimal monthlyPrice) {
        Plan plan = new Plan();
        plan.code = code;
        plan.name = name;
        plan.monthlyPrice = monthlyPrice;
        plan.active = true;
        return plan;
    }

    public void updateDetails(String name, BigDecimal monthlyPrice) {
        this.name = name;
        this.monthlyPrice = monthlyPrice;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
