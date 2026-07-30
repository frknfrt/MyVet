package com.vetos.modules.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "branches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    private String address;

    private String city;

    @Column(name = "tarbil_branch_code")
    private String tarbilBranchCode;

    private String timezone;

    public static Branch create(UUID tenantId, String name) {
        Branch branch = new Branch();
        branch.tenantId = tenantId;
        branch.name = name;
        branch.timezone = "Europe/Istanbul";
        return branch;
    }

    public void updateDetails(String address, String city, String timezone, String tarbilBranchCode) {
        this.address = address;
        this.city = city;
        this.timezone = timezone;
        this.tarbilBranchCode = tarbilBranchCode;
    }
}
