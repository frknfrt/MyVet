# Kiracı İzolasyonu — Ek Tur Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** `tenant_id` sütunu olan ama Hibernate kiracı filtresi taşımayan 15 varlığa `@TenantId` ekleyerek başka kiracının kaydına kimlikle erişimi kapatmak.

**Architecture:** 2026-09-17 tasarımının §3/§10 mekanizması (root oturum bağlamsız yolları korur). Migration yok.

**Spec:** `docs/superpowers/specs/2026-09-17-kiraci-izolasyonu-sertlestirme-design.md` §11.

## Global Constraints
- Doğrudan `main`; push yok. Migration yok (sütunlar mevcut, NOT NULL).
- Dışarıda kalanlar: NotificationSettings, NotificationLog, TarbilExtensionToken, TarbilSubmission, TarbilValueMapping, TarbilStockSnapshot(+Line).
- Backend testleri dev Postgres'i (5433) kullanır; çalışan 8080 backend'i yeniden başlatılmalı (yalnız kod değişikliği, şema değil).

## Review Focus
1. Bağlamsız (root) akışlar bozulmamalı: giriş, herkese açık randevu/kayıt, platform yönetimi, zamanlayıcılar → mevcut testler + `rootSession_worksAndSeesAllTenants_whenNoTenantContext`.
2. JWT isteğinde başka kiracı adına kayıt oluşturan bir yol varsa Hibernate persist'i reddeder → tam test takımı.
3. Başka kiracının aşı kaydına `/administer`, `/cancel` → 404, durum değişmez.

### Task 1: İzolasyon testleri (RED)
- [ ] `TenantIsolationTest`: Appointment, ServiceType, Invoice, BoardingRoom, BoardingStay, VaccinationRecord, ImagingRecord, LabResult, EInvoiceSubmission, MessageTemplate, Owner, PlatformInvoice, Branch, StaffInvite, Subscription için `<varlik>_isIsolatedAcrossTenants`; `purgeTenant`'a yeni tablolar.
- [ ] `TarbilExtensionSecurityIntegrationTest`: `foreignVaccinationCannotBeAdministeredOrCancelled`.
- [ ] Çalıştır → yeni testler başarısız.

### Task 2: `@TenantId` (GREEN)
- [ ] 15 varlıkta `@Column(name = "tenant_id", nullable = false)` → `@org.hibernate.annotations.TenantId` + `@Column(name = "tenant_id", nullable = false, updatable = false)`.
- [ ] Tam backend takımı yeşil; kırılan akış olursa kök nedeni bulunur (bağlam köprüsü / root), gerekirse ledger'a Ruling.

### Task 3: Belgeler
- [ ] `docs/api-conventions.md` kiracı izolasyonu notu; commit.
