-- Yayin anindaki tum deneme hesaplari test ekibine aittir (spec 2026-11-13 S7):
-- suresiz, tam yetkili test hesabi (INTERNAL) olurlar. Bu migration yalnizca o an
-- var olan kayitlari etkiler; sonradan vitrinden acilan denemeler TRIAL kalir.
UPDATE tenants SET status = 'ACTIVE'
 WHERE status = 'TRIAL'
   AND id IN (SELECT tenant_id FROM subscriptions WHERE plan_code = 'TRIAL');

UPDATE subscriptions
   SET plan_code = 'INTERNAL', billing_status = 'ACTIVE', renews_at = NULL
 WHERE plan_code = 'TRIAL';
