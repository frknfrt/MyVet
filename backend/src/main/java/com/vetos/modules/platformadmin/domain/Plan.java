package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "annual_price")
    private BigDecimal annualPrice;

    @Column
    private String description;

    @Column
    private String badge;

    @Column(name = "image_url")
    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "feature", nullable = false)
    private List<String> features = new ArrayList<>();

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

    public void updateDetails(
        String name,
        BigDecimal monthlyPrice,
        BigDecimal annualPrice,
        String description,
        String badge,
        String imageUrl,
        List<String> features
    ) {
        this.name = name;
        this.monthlyPrice = monthlyPrice;
        this.annualPrice = annualPrice;
        this.description = description;
        this.badge = badge;
        this.imageUrl = imageUrl;
        this.features = new ArrayList<>(features);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
