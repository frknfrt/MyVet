package com.vetos.modules.platformadmin.application.dto;

/**
 * Tenant Detayi sayfasindaki "Entegrasyon Durumu" ozeti -- tek bir kiracinin
 * suan FAILED durumdaki e-Fatura ve TARBIL senkron sayilari (bkz.
 * GetTenantIntegrationHealthUseCase).
 */
public record TenantIntegrationHealth(long failedEInvoiceCount, long failedTarbilSyncCount) {}
