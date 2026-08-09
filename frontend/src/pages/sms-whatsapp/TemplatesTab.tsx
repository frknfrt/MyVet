import { FormEvent, useEffect, useState } from 'react';
import { MessageTemplate, MessageTemplateInput, TemplateChannel, templateApi } from '../../api/campaignApi';
import { ApiError } from '../../api/client';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './SmsWhatsappPage.module.css';
import templateStyles from './TemplatesTab.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const CHANNEL_LABELS: Record<TemplateChannel, string> = { SMS: 'SMS', WHATSAPP: 'WhatsApp', BOTH: 'SMS + WhatsApp' };

const EMPTY_FORM: MessageTemplateInput = { name: '', channel: 'SMS', category: 'Genel', body: '' };

export function TemplatesTab() {
  const [templates, setTemplates] = useState<MessageTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<MessageTemplateInput>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  function load() {
    setLoading(true);
    templateApi
      .list()
      .then(setTemplates)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function openCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setModalOpen(true);
  }

  function openEdit(t: MessageTemplate) {
    setEditingId(t.id);
    setForm({ name: t.name, channel: t.channel, category: t.category, body: t.body });
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      if (editingId) await templateApi.update(editingId, form);
      else await templateApi.create(form);
      setModalOpen(false);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string) {
    if (!window.confirm('Bu şablonu silmek istediğinize emin misiniz?')) return;
    try {
      await templateApi.remove(id);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    }
  }

  return (
    <div>
      <div className={templateStyles.actionsRow}>
        <Button variant="primary" onClick={openCreate}>
          + Yeni Şablon
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.tableCard}>
        <div className={templateStyles.tableHead}>
          <div>Ad</div>
          <div>Kanal</div>
          <div>Kategori</div>
          <div>Gövde</div>
          <div></div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : templates.length === 0 ? (
          <div className={styles.empty}>Henüz şablon oluşturulmadı</div>
        ) : (
          templates.map((t) => (
            <div key={t.id} className={templateStyles.tableRow}>
              <div>{t.name}</div>
              <div>
                <Badge tone="neutral">{CHANNEL_LABELS[t.channel]}</Badge>
              </div>
              <div className={styles.muted}>{t.category}</div>
              <div className={templateStyles.bodyPreview}>{t.body}</div>
              <div className={templateStyles.rowActions}>
                <Button variant="secondary" onClick={() => openEdit(t)}>
                  Düzenle
                </Button>
                <Button variant="danger" onClick={() => handleDelete(t.id)}>
                  Sil
                </Button>
              </div>
            </div>
          ))
        )}
      </div>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} width={520}>
        <form onSubmit={handleSubmit}>
          <div className={templateStyles.modalTitle}>{editingId ? 'Şablonu Düzenle' : 'Yeni Şablon'}</div>
          <FieldWrap label="Ad">
            <Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required />
          </FieldWrap>
          <FieldWrap label="Kanal">
            <Select value={form.channel} onChange={(e) => setForm((f) => ({ ...f, channel: e.target.value as TemplateChannel }))}>
              <option value="SMS">SMS</option>
              <option value="WHATSAPP">WhatsApp</option>
              <option value="BOTH">SMS + WhatsApp</option>
            </Select>
          </FieldWrap>
          <FieldWrap label="Kategori">
            <Input value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))} required />
          </FieldWrap>
          <FieldWrap label="Mesaj gövdesi">
            <Textarea rows={6} value={form.body} onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))} required />
          </FieldWrap>
          <div className={templateStyles.modalActions}>
            <Button type="button" variant="secondary" onClick={() => setModalOpen(false)}>
              Vazgeç
            </Button>
            <Button type="submit" variant="primary" disabled={saving}>
              {saving ? 'Kaydediliyor...' : 'Kaydet'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
