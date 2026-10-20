import { useEffect, useState } from 'react';
import { ExtensionToken, PairingCode, tarbilApi } from '../../api/tarbilApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import {
  EXTENSION_DOWNLOAD_URL,
  ExtensionVersionStatus,
  extensionVersionStatus,
  fetchLatestExtensionVersion,
} from '../../lib/extensionRelease';
import { getInstalledExtensionVersion } from '../../lib/tarbilExtension';
import { compareVersions } from '../../lib/version';
import styles from './SettingsPage.module.css';

export function TarbilExtensionCard() {
  const [tokens, setTokens] = useState<ExtensionToken[]>([]);
  const [code, setCode] = useState<PairingCode | null>(null);
  const [busy, setBusy] = useState(false);
  const [release, setRelease] = useState<{ installed: string | null; latest: string | null } | null>(null);

  useEffect(() => {
    Promise.all([getInstalledExtensionVersion(), fetchLatestExtensionVersion()]).then(([installed, latest]) =>
      setRelease({ installed, latest }),
    );
  }, []);

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

      {release && (
        <div className={styles.extensionRelease}>
          {(() => {
            const status = extensionVersionStatus(release.installed, release.latest);
            return (
              <div className={status.kind === 'outdated' ? styles.versionWarning : styles.muted}>{versionText(status, release.latest)}</div>
            );
          })()}
          {release.latest ? (
            <a className={styles.downloadLink} href={`${EXTENSION_DOWNLOAD_URL}?v=${release.latest}`} download>
              Eklentiyi indir ({release.latest})
            </a>
          ) : (
            <div className={styles.muted}>Eklenti dosyası şu an hazır değil</div>
          )}
          <details className={styles.installSteps}>
            <summary>Kurulum ve güncelleme</summary>
            <ol>
              <li>İndirdiğiniz zip dosyasını bir klasöre açın (ör. Belgeler\Vetly TARBİL).</li>
              <li>
                Chrome adres çubuğuna <code>chrome://extensions</code> yazın ve sağ üstten "Geliştirici modu"nu açın.
              </li>
              <li>"Paketlenmemiş öğe yükle"ye basıp açtığınız klasörü seçin.</li>
              <li>
                Güncellerken: aynı klasörün içeriğini yeni zip'teki dosyalarla değiştirin, eklentiler sayfasında eklentinin
                "Yenile" düğmesine basın. Eşleştirme korunur, yeniden kod girmeniz gerekmez.
              </li>
            </ol>
          </details>
        </div>
      )}

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
              {t.lastUsedAt && !t.revokedAt && <DeviceVersion version={t.lastClientVersion} latest={release?.latest ?? null} />}
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

/** Cihazın son görülen sürümü; en düşük sürümü yükseltmeden önce kimin güncellemediği buradan görülür. */
function DeviceVersion({ version, latest }: { version: string | null; latest: string | null }) {
  if (!version) return <span className={styles.versionWarning}> · Sürüm bilinmiyor (0.2.0 öncesi)</span>;
  if (latest && compareVersions(version, latest) < 0) {
    return <span className={styles.versionWarning}> · Sürüm {version} (eski)</span>;
  }
  return <span> · Sürüm {version}</span>;
}

function versionText(status: ExtensionVersionStatus, latest: string | null): string {
  switch (status.kind) {
    case 'not-installed':
      return latest ? 'Eklenti yüklü değil. İndirip kurun.' : 'Eklenti yüklü değil.';
    case 'installed':
      return `Yüklü sürüm: ${status.installed}`;
    case 'up-to-date':
      return `Yüklü sürüm: ${status.installed} (güncel)`;
    case 'outdated':
      return `Yüklü sürüm: ${status.installed}. Yeni sürüm var (${status.latest}), indirip güncelleyin.`;
  }
}
