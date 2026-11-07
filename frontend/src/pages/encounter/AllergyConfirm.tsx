import { ReactNode, useState } from 'react';
import { AllergyAck, AllergyCheck, clinicalApi } from '../../api/clinicalApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './AllergyConfirm.module.css';

const MIN_REASON_LENGTH = 3;

interface Pending {
  check: AllergyCheck;
  resolve: (ack: AllergyAck | null) => void;
}

/**
 * Reçete ve malzeme eklemeden önce alerji onayı. Hastanın kritik uyarısı yoksa sessizce geçer;
 * varsa pencere açar. Eşleşen ilaçta gerekçe zorunludur (sunucu da aynı kuralı uygular, bkz. DrugAllergyGuard).
 * confirm() null dönerse kullanıcı vazgeçmiştir.
 */
export function useAllergyConfirm(patientId: string): {
  confirm: (drugIds: string[], inventoryItemIds: string[]) => Promise<AllergyAck | null>;
  modal: ReactNode;
} {
  const [pending, setPending] = useState<Pending | null>(null);

  async function confirm(drugIds: string[], inventoryItemIds: string[]): Promise<AllergyAck | null> {
    const check = await clinicalApi.checkAllergies({ patientId, drugIds, inventoryItemIds });
    if (!check.criticalAlert) return {};
    return new Promise((resolve) => setPending({ check, resolve }));
  }

  function finish(ack: AllergyAck | null) {
    pending?.resolve(ack);
    setPending(null);
  }

  const modal = pending && (
    <AllergyConfirmModal
      check={pending.check}
      onCancel={() => finish(null)}
      onConfirm={(reason) => finish({ allergyAcknowledged: true, allergyOverrideReason: reason || null })}
    />
  );
  return { confirm, modal };
}

interface AllergyConfirmModalProps {
  check: AllergyCheck;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}

export function AllergyConfirmModal({ check, onCancel, onConfirm }: AllergyConfirmModalProps) {
  const [reason, setReason] = useState('');
  const matched = check.matches.length > 0;
  const reasonOk = !matched || reason.trim().length >= MIN_REASON_LENGTH;

  return (
    <Modal open onClose={onCancel} width={520} dirty={reason.trim().length > 0}>
      <div role="alertdialog" aria-labelledby="allergy-title" aria-describedby="allergy-alert">
        <h2 id="allergy-title" className={styles.title}>
          {matched ? 'Alerjiyle eşleşen ilaç' : 'Hastanın kayıtlı uyarısı var'}
        </h2>
        <div id="allergy-alert" className={styles.alert}>
          ⚠ {check.criticalAlert}
        </div>

        {matched ? (
          <>
            <ul className={styles.matches}>
              {check.matches.map((m) => (
                <li key={m.drugName}>
                  <strong>{m.drugName}</strong> — {m.reason}
                </li>
              ))}
            </ul>
            <FieldWrap label="Neden yine de ekliyorsunuz? (zorunlu, işlem geçmişine kaydedilir)">
              <Textarea
                rows={2}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Örn. alerji kaydı hatalı, sahibiyle doğrulandı"
                maxLength={500}
                autoFocus
              />
            </FieldWrap>
          </>
        ) : (
          <p className={styles.note}>
            Seçtiğiniz ilaç bu uyarıyla otomatik olarak eşleşmedi. Etken maddesi kayıtlı olmayan stok ürünlerinde eşleşme
            yapılamaz; ilacın uyarıyla çelişmediğini kontrol edin.
          </p>
        )}

        <div className={styles.actions}>
          <Button variant="secondary" onClick={onCancel}>
            Vazgeç
          </Button>
          <Button variant={matched ? 'danger' : 'primary'} onClick={() => onConfirm(reason.trim())} disabled={!reasonOk}>
            {matched ? 'Gerekçeyle ekle' : 'Kontrol ettim, ekle'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

/** Muayene ekranında hasta başlığının altında sürekli görünen uyarı. */
export function CriticalAlertBanner({ text }: { text: string | null | undefined }) {
  if (!text || !text.trim()) return null;
  return (
    <div className={styles.banner} role="alert">
      ⚠ <strong>Kritik uyarı:</strong> {text}
    </div>
  );
}
