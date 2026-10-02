import { useEffect, useState } from 'react';
import { ExtensionToken, PairingCode, tarbilApi } from '../../api/tarbilApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import styles from './SettingsPage.module.css';

export function TarbilExtensionCard() {
  const [tokens, setTokens] = useState<ExtensionToken[]>([]);
  const [code, setCode] = useState<PairingCode | null>(null);
  const [busy, setBusy] = useState(false);

  function reload() {
    tarbilApi.extensionTokens().then(setTokens);
  }

  useEffect(() => {
    reload();
  }, []);

  async function handleCreateCode() {
    setBusy(true);
    try {
      setCode(await tarbilApi.createPairingCode());
    } finally {
      setBusy(false);
    }
  }

  async function handleRevoke(id: string) {
    setBusy(true);
    try {
      await tarbilApi.revokeExtensionToken(id);
      reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.integrationCard}>
      <div className={styles.integrationHeader}>
        <div>
          <div className={styles.integrationName}>TARBİL Chrome Eklentisi</div>
          <div className={styles.integrationDesc}>
            Eklentiyi bağlamak için kod oluşturun ve eklentinin yan panelinde girin. Kod 10 dakika geçerlidir ve bir kez
            kullanılabilir.
          </div>
        </div>
        <Button variant="secondary" onClick={handleCreateCode} disabled={busy}>
          Eklentiyi bağla
        </Button>
      </div>

      {code && (
        <div className={styles.lastSynced}>
          Eşleştirme kodu: <strong>{code.code}</strong> — {new Date(code.expiresAt).toLocaleTimeString('tr-TR')} saatine
          kadar geçerli
        </div>
      )}

      {tokens.length === 0 ? (
        <div className={styles.empty}>Bağlı eklenti yok</div>
      ) : (
        tokens.map((t) => (
          <div key={t.id} className={styles.row}>
            <div>{t.label ?? 'Eklenti'}</div>
            <div className={styles.muted}>{t.staffName}</div>
            <div className={styles.muted}>
              {t.lastUsedAt ? `Son kullanım: ${new Date(t.lastUsedAt).toLocaleString('tr-TR')}` : 'Henüz kullanılmadı'}
            </div>
            <div>{t.revokedAt ? <Badge tone="neutral">İptal edildi</Badge> : <Badge tone="success">Aktif</Badge>}</div>
            <div>
              {!t.revokedAt && (
                <Button variant="danger" onClick={() => handleRevoke(t.id)} disabled={busy}>
                  İptal et
                </Button>
              )}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
