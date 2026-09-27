-- Kiraci basina bildirim kanali tercihi: randevu onayi/hatirlatmasi mesajlarinin
-- SMS'ten mi yoksa WhatsApp'tan mi gonderilecegini klinik kendisi secebilsin diye
-- (bkz. NotificationSettings, GetNotificationSettingsUseCase, UpdateNotificationSettingsUseCase).
-- Satir yoksa uygulama tarafinda SMS varsayilan kabul edilir (bkz.
-- SendAppointmentRemindersUseCase / AppointmentScheduledEventListener).
CREATE TABLE notification_settings (
    tenant_id UUID PRIMARY KEY,
    appointment_channel VARCHAR(20) NOT NULL DEFAULT 'SMS'
);
