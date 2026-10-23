import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { AdminSentEmail, EmailTemplateCode, EmailTemplateOption, platformAdminApi } from '../../api/platformAdminApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import styles from './PlatformAdminPages.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('tr-TR');
}

export function AdminEmailSenderPage() {
  const [templates, setTemplates] = useState<EmailTemplateOption[]>([]);
  const [selectedCode, setSelectedCode] = useState<EmailTemplateCode | ''>('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [recipientEmail, setRecipientEmail] = useState('');

  const [previewSubject, setPreviewSubject] = useState<string | null>(null);
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [sending, setSending] = useState(false);

  const [history, setHistory] = useState<AdminSentEmail[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);

  const [error, setError] = useState<string | null>(null);

  const selectedTemplate = templates.find((t) => t.code === selectedCode) ?? null;

  function loadHistory() {
    setHistoryLoading(true);
    platformAdminApi
      .listSentAdminEmails()
      .then(setHistory)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setHistoryLoading(false));
  }

  useEffect(() => {
    platformAdminApi.listEmailTemplates().then(setTemplates).catch((err) => setError(errorMessageOf(err)));
    loadHistory();
  }, []);

  function handleTemplateChange(code: string) {
    setSelectedCode(code as EmailTemplateCode | '');
    setFields({});
    setPreviewSubject(null);
    setPreviewHtml(null);
  }

  function handleFieldChange(key: string, value: string) {
    setFields((prev) => ({ ...prev, [key]: value }));
  }

  async function handlePreview() {
    if (!selectedTemplate) return;
    setError(null);
    setPreviewLoading(true);
    try {
      const result = await platformAdminApi.previewAdminEmail({ templateType: selectedTemplate.code, fields });
      setPreviewSubject(result.subject);
      setPreviewHtml(result.html);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setPreviewLoading(false);
    }
  }

  async function handleSend(e: FormEvent) {
    e.preventDefault();
    if (!selectedTemplate) return;
    if (!window.confirm(`Bu e-posta ${selectedTemplate.mailboxAddress} adresinden ${recipientEmail} adresine gönderilecek. Onaylıyor musun?`)) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      await platformAdminApi.sendAdminEmail({ templateType: selectedTemplate.code, recipientEmail, fields });
      setRecipientEmail('');
      setFields({});
      setPreviewSubject(null);
      setPreviewHtml(null);
      loadHistory();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSending(false);
    }
  }

  return (
    <div>
      <div className={styles.title}>E-posta Gönder</div>
      <p className={styles.note}>
        Satış, deneme takibi, hoş geldin ve fatura hatırlatma e-postalarını markalı HTML şablonlarla buradan
        gönderebilirsin. Her şablon doğru kutudan gider (örnek: fatura hatırlatması her zaman bildirim@vetly.com.tr'den).
        cPanel SMTP bilgileri sunucuda tanımlı değilse gönderim gerçekleşmez, sadece sunucu loglarına yazılır.
      </p>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.emailFormLayout}>
        <div className={styles.card}>
          <form onSubmit={handleSend}>
            <FieldWrap label="Şablon">
              <Select value={selectedCode} onChange={(e) => handleTemplateChange(e.target.value)} required>
                <option value="" disabled>
                  Seçiniz
                </option>
                {templates.map((t) => (
                  <option key={t.code} value={t.code}>
                    {t.label} ({t.mailboxAddress})
                  </option>
                ))}
              </Select>
            </FieldWrap>

            {selectedTemplate && (
              <>
                <FieldWrap label="Alıcı E-posta">
                  <Input
                    type="email"
                    value={recipientEmail}
                    onChange={(e) => setRecipientEmail(e.target.value)}
                    required
                  />
                </FieldWrap>

                {selectedTemplate.fields.map((f) => (
                  <FieldWrap key={f.key} label={f.label}>
                    <Input value={fields[f.key] ?? ''} onChange={(e) => handleFieldChange(f.key, e.target.value)} required />
                  </FieldWrap>
                ))}

                <div className={styles.modalActions}>
                  <Button type="button" variant="secondary" disabled={previewLoading} onClick={handlePreview}>
                    {previewLoading ? 'Önizleniyor...' : 'Önizle'}
                  </Button>
                  <Button type="submit" variant="primary" disabled={sending}>
                    {sending ? 'Gönderiliyor...' : 'Gönder'}
                  </Button>
                </div>
              </>
            )}
          </form>
        </div>

        <div>
          {previewHtml ? (
            <>
              <div className={styles.cardTitle} style={{ marginBottom: 8 }}>
                Konu: {previewSubject}
              </div>
              <iframe title="E-posta önizleme" srcDoc={previewHtml} className={styles.previewFrame} />
            </>
          ) : (
            <div className={styles.empty}>Önizlemek için alanları doldurup "Önizle"ye bas</div>
          )}
        </div>
      </div>

      <div className={styles.cardTitle} style={{ margin: '24px 0 8px 0' }}>
        Gönderim Geçmişi
      </div>
      <div className={styles.tableCard}>
        <div className={[styles.tableHead, styles.sentEmailRow].join(' ')}>
          <div>Tarih</div>
          <div>Şablon</div>
          <div>Alıcı</div>
          <div>Gönderen</div>
        </div>
        {historyLoading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : history.length === 0 ? (
          <div className={styles.empty}>Henüz e-posta gönderilmedi</div>
        ) : (
          history.map((h) => (
            <div key={h.id} className={[styles.row, styles.sentEmailRow].join(' ')} style={{ cursor: 'default' }} title={h.subject}>
              <div className={styles.muted}>{formatDateTime(h.sentAt)}</div>
              <div>{h.templateLabel}</div>
              <div className={styles.muted}>{h.recipientEmail}</div>
              <div className={styles.muted}>{h.sentByAdminEmail}</div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
