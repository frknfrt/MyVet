-- Kiraci + bildirim tipi basina ozellestirilebilir otomatik mesaj metni
-- (bkz. NotificationMessageTemplate, NotificationTemplateDefaults,
-- GetNotificationTemplatesUseCase, UpdateNotificationTemplateUseCase).
-- Sadece randevu onayi/hatirlatmasi ve asi hatirlatmasi icin gecerli;
-- satir yoksa uygulama NotificationTemplateDefaults'taki varsayilan metni
-- kullanir. CAMPAIGN_MESSAGE bu tabloya YAZILMAZ -- o zaten her gonderimde
-- elle yaziliyor (bkz. SendCampaignUseCase, message_templates tablosu --
-- ayri, ilgisiz bir kavram: kullanicinin elle secip kullandigi hazir
-- metinler).
CREATE TABLE notification_message_templates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    template_text TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_message_templates_tenant_type UNIQUE (tenant_id, notification_type)
);
