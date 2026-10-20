import { ReactNode, useEffect, useState } from 'react';
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
import settingsStyles from './SettingsPage.module.css';
import styles from './TarbilExtensionCard.module.css';

const EXTENSION_NAME = 'Vetly TARBİL Yardımcısı';

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

  const status = release ? extensionVersionStatus(release.installed, release.latest) : null;
  const latest = release?.latest ?? null;
  const installed = status !== null && status.kind !== 'not-installed';
  const activeTokens = tokens.filter((t) => !t.revokedAt);
  const revokedTokens = tokens.filter((t) => t.revokedAt);

  return (
    <div className={`${settingsStyles.integrationCard} ${styles.card}`}>
      <div className={styles.header}>
        <div>
          <div className={settingsStyles.integrationName}>TARBİL Chrome Eklentisi</div>
          <div className={settingsStyles.integrationDesc}>
            Vetly'deki aşı ve reçete kayıtlarını TARBİL formlarına sizin yerinize doldurur. Kaydet'e her zaman siz
            basarsınız.
          </div>
        </div>
        {status && <StatusBadge status={status} />}
      </div>

      {status && <div className={styles.statusLine}>{versionText(status, latest)}</div>}

      {status?.kind === 'outdated' && (
        <div className={styles.updateBox}>
          <div className={styles.updateLead}>
            <div className={styles.updateTitle}>Yeni sürüm hazır ({status.latest})</div>
            <DownloadButton latest={latest} label="Güncellemeyi indir" />
            <div className={styles.hint}>Bağlantınız korunur, yeniden kod girmeniz gerekmez.</div>
          </div>
          <ol className={styles.subSteps}>
            <li>Yukarıdaki düğmeyle yeni dosyayı indirin.</li>
            <li>
              Bilgisayarınızdaki eski <strong>vetly-tarbil-eklenti</strong> klasörünü açın ve içindekilerin hepsini
              silin (klasörün kendisini silmeyin).
            </li>
            <li>
              İndirdiğiniz zip dosyasına çift tıklayın. İçindeki her şeyi seçip (Ctrl+A) kopyalayın (Ctrl+C) ve boşalttığınız
              klasöre yapıştırın (Ctrl+V).
            </li>
            <li>
              Chrome'da <code>chrome://extensions</code> sayfasını açın, <strong>{EXTENSION_NAME}</strong> kartındaki
              yuvarlak ok (Yeniden yükle) simgesine basın.
            </li>
          </ol>
        </div>
      )}

      <div className={styles.sectionTitle}>Kurulum</div>
      <ol className={styles.steps}>
        <Step n={1} title="Eklentiyi indirin" done={installed}>
          {installed ? (
            <div className={styles.hint}>Bu bilgisayarda kurulu. Başka bir bilgisayar için yine buradan indirebilirsiniz.</div>
          ) : null}
          <DownloadButton latest={latest} label="Eklentiyi indir" />
        </Step>

        <Step n={2} title="Chrome'a ekleyin" done={installed}>
          <details className={styles.details} open={!installed}>
            <summary>Adım adım göster</summary>
            <ol className={styles.subSteps}>
              <li>
                İndirilenler klasöründeki <strong>vetly-tarbil-eklenti.zip</strong> dosyasına sağ tıklayın,{' '}
                <strong>Tümünü ayıkla</strong> ve ardından <strong>Ayıkla</strong>'ya basın.
              </li>
              <li>
                Oluşan <strong>vetly-tarbil-eklenti</strong> klasörünü <strong>Belgeler</strong>'e taşıyın. Bu klasörü
                silmeyin; eklenti bu klasörden çalışır.
              </li>
              <li>
                Chrome'da yeni bir sekme açın, adres çubuğuna <code>chrome://extensions</code> yazıp Enter'a basın.
              </li>
              <li>
                Sağ üst köşedeki <strong>Geliştirici modu</strong> anahtarını açın.
              </li>
              <li>
                Sol üstte beliren <strong>Paketlenmemiş öğe yükle</strong> düğmesine basın ve Belgeler'deki{' '}
                <strong>vetly-tarbil-eklenti</strong> klasörünü seçin.
              </li>
              <li>
                Chrome'un sağ üstündeki yapboz simgesine basın, <strong>{EXTENSION_NAME}</strong>'nın yanındaki
                raptiye simgesine basın. Eklentinin simgesi artık hep görünür.
              </li>
            </ol>
          </details>
        </Step>

        <Step n={3} title="Vetly'ye bağlayın">
          <div className={styles.hint}>
            Her bilgisayarda bir kez yapılır. Kod 10 dakika geçerlidir ve bir kez kullanılabilir.
          </div>
          <Button variant="secondary" onClick={handleCreateCode} disabled={busy}>
            Eklentiyi bağla
          </Button>
          {code && (
            <div className={styles.codeBox}>
              <div className={styles.code}>{code.code}</div>
              <div className={styles.hint}>
                {new Date(code.expiresAt).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })} saatine
                kadar geçerli
              </div>
              <ol className={styles.subSteps}>
                <li>Chrome'un sağ üstündeki Vetly simgesine basın; sağda bir panel açılır.</li>
                <li>Bu kodu ve bilgisayarın adını (ör. Muayene 1) yazıp <strong>Bağlan</strong>'a basın.</li>
              </ol>
            </div>
          )}
        </Step>
      </ol>

      <div className={styles.sectionTitle}>Bağlı bilgisayarlar</div>
      {activeTokens.length === 0 ? (
        <div className={styles.hint}>Henüz bağlı bilgisayar yok.</div>
      ) : (
        <ul className={styles.devices}>
          {activeTokens.map((t) => (
            <li key={t.id} className={styles.device}>
              <div>
                <div className={styles.deviceName}>{t.label ?? 'Eklenti'}</div>
                <div className={styles.deviceMeta}>
                  {t.staffName} ·{' '}
                  {t.lastUsedAt ? `Son kullanım ${new Date(t.lastUsedAt).toLocaleString('tr-TR')}` : 'Henüz kullanılmadı'}
                  {t.lastUsedAt && <DeviceVersion version={t.lastClientVersion} latest={latest} />}
                </div>
              </div>
              <Button variant="danger" onClick={() => handleRevoke(t.id)} disabled={busy}>
                Bağlantıyı kaldır
              </Button>
            </li>
          ))}
        </ul>
      )}
      {revokedTokens.length > 0 && (
        <details className={styles.details}>
          <summary>Kaldırılan bağlantılar ({revokedTokens.length})</summary>
          <ul className={styles.devices}>
            {revokedTokens.map((t) => (
              <li key={t.id} className={`${styles.device} ${styles.deviceRevoked}`}>
                <div>
                  <div className={styles.deviceName}>{t.label ?? 'Eklenti'}</div>
                  <div className={styles.deviceMeta}>{t.staffName}</div>
                </div>
                <Badge tone="neutral">Kaldırıldı</Badge>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function Step({ n, title, done, children }: { n: number; title: string; done?: boolean; children: ReactNode }) {
  return (
    <li className={styles.step}>
      <span className={done ? `${styles.stepNumber} ${styles.stepDone}` : styles.stepNumber}>{done ? '✓' : n}</span>
      <div className={styles.stepBody}>
        <div className={styles.stepTitle}>{title}</div>
        {children}
      </div>
    </li>
  );
}

function DownloadButton({ latest, label }: { latest: string | null; label: string }) {
  if (!latest) return <div className={styles.hint}>Eklenti dosyası şu an hazır değil.</div>;
  return (
    <a className={styles.downloadButton} href={`${EXTENSION_DOWNLOAD_URL}?v=${latest}`} download>
      {label} ({latest})
    </a>
  );
}

function StatusBadge({ status }: { status: ExtensionVersionStatus }) {
  switch (status.kind) {
    case 'not-installed':
      return <Badge tone="neutral">Kurulu değil</Badge>;
    case 'outdated':
      return <Badge tone="warning">Güncelleme var</Badge>;
    default:
      return <Badge tone="success">Kurulu</Badge>;
  }
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
      return latest
        ? 'Bu bilgisayarda eklenti yüklü değil. Aşağıdaki 3 adımı izleyin.'
        : 'Bu bilgisayarda eklenti yüklü değil.';
    case 'installed':
      return `Yüklü sürüm: ${status.installed}`;
    case 'up-to-date':
      return `Yüklü sürüm: ${status.installed} (güncel)`;
    case 'outdated':
      return `Yüklü sürüm: ${status.installed}. Yeni sürüm var (${status.latest}), aşağıdan güncelleyin.`;
  }
}
