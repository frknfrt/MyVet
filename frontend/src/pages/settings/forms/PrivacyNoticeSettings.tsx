import { useEffect, useState } from 'react';
import { formsApi, type PrivacyNotice } from '../../../api/formsApi';
import { Button } from '../../../components/ui/Button';
import styles from './PrivacyNoticeSettings.module.css';
import { PrivacyNoticeLink } from '../../../forms/PrivacyNoticeLink';

export function PrivacyNoticeSettings() {
  const [notice,setNotice] = useState<PrivacyNotice|null>(null);
  const [text,setText] = useState('');
  const [error,setError] = useState<string|null>(null);
  const [saved,setSaved] = useState(false);
  const [busy,setBusy] = useState(false);
  const [loading,setLoading] = useState(true);
  const [attempt,setAttempt] = useState(0);
  useEffect(() => {
    let active=true;
    setLoading(true);setError(null);
    formsApi.getPrivacyNotice().then(n => {
      if(active) { setNotice(n);setText(n.text); }
    }).catch(e => {
      if(active) setError(e instanceof Error ? e.message : 'Metin yüklenemedi');
    }).finally(() => { if(active) setLoading(false); });
    return () => {active=false;};
  },[attempt]);
  const save = async () => {
    if(!notice || busy) return;
    setBusy(true);setError(null);setSaved(false);
    try { const n=await formsApi.savePrivacyNotice(text,notice.version);setNotice(n);setText(n.text);setSaved(true); }
    catch(e) {setError(e instanceof Error ? e.message : 'Metin kaydedilemedi');}
    finally {setBusy(false);}
  };
  const changed = !!notice && text !== notice.text;
  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerIcon} aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M12 3 4 6v6c0 4 5 7 8 9 3-2 8-5 8-9V6l-8-3Z"/><path d="m8.5 12 2.3 2.3 4.7-4.8"/></svg>
        </div>
        <div>
          <span className={styles.eyebrow}>FORM AYARLARI</span>
          <h2>KVKK aydınlatma metni</h2>
          <p>Hasta sahiplerine sunulan bilgilendirme metnini kliniğinize göre düzenleyin.</p>
        </div>
      </header>

      {loading ? (
        <div className={styles.stateCard} role="status">
          <span className={styles.spinner} aria-hidden="true" />
          <p>Metin yükleniyor…</p>
        </div>
      ) : !notice ? (
        <div className={styles.stateCard}>
          <h3>Metin yüklenemedi</h3>
          <p className={styles.errorText} role="alert">{error ?? 'Aydınlatma metnine şu anda ulaşılamıyor.'}</p>
          <Button type="button" onClick={() => setAttempt(n => n+1)}>Tekrar dene</Button>
        </div>
      ) : (
        <div className={styles.layout}>
          <div className={styles.editorCard}>
            <div className={styles.editorHeader}>
              <div>
                <label htmlFor="clinic-privacy-notice">Aydınlatma metni</label>
                <p>Klinik bilgilerinizi ve veri işleme süreçlerinizi açıkça belirtin.</p>
              </div>
              <span className={styles.versionBadge}>Sürüm {notice.version}</span>
            </div>
            <div className={styles.editorBody}>
              <textarea id="clinic-privacy-notice" rows={20} maxLength={20000} value={text}
                aria-describedby="privacy-notice-hint"
                className={styles.editor}
                onChange={e => {setText(e.target.value);setSaved(false);}} />
              <div className={styles.editorMeta}>
                <span id="privacy-notice-hint">Hasta sahibine sunulacak metnin tamamı.</span>
                <span>{text.length.toLocaleString('tr-TR')} / 20.000 karakter</span>
              </div>
            </div>
            {error && <div className={styles.errorBanner} role="alert">{error}</div>}
            <footer className={styles.editorFooter}>
              <div className={styles.saveStatus}>
                <span className={`${styles.statusDot} ${changed ? styles.dirtyDot : styles.savedDot}`} aria-hidden="true" />
                {saved ? <span role="status">Yeni sürüm kaydedildi.</span> : changed ? 'Kaydedilmemiş değişiklikler' : 'Metin güncel'}
              </div>
              <Button type="button" variant="primary" disabled={busy || !text.trim() || !changed} onClick={save}>
                {busy ? 'Kaydediliyor…' : 'Yeni sürüm olarak kaydet'}
              </Button>
            </footer>
          </div>

          <aside className={styles.aside}>
            <section className={styles.infoCard}>
              <span className={styles.cardEyebrow}>YAYIN BİLGİSİ</span>
              <h3>Metin ve sürüm</h3>
              <dl className={styles.details}>
                <div><dt>Güncel sürüm</dt><dd>{notice.version}</dd></div>
                <div><dt>Geçerlilik</dt><dd>Bu klinik</dd></div>
              </dl>
              <div className={styles.previewArea}>
                <p>Metni hasta sahibinin göreceği pencerede inceleyin.</p>
                <PrivacyNoticeLink text={text} version={notice.version} label="Metni önizle" variant="secondary" className={styles.previewButton}/>
              </div>
            </section>
            <section className={styles.noteCard}>
              <h3>Önceki imzalar korunur</h3>
              <p>Kaydettiğiniz değişiklikler yalnız sonraki imzalarda kullanılır. Önceden imzalanmış formlardaki metin değişmez.</p>
              <div className={styles.noteDivider}/>
              <p>Veri sorumlusu kimliğini, iletişim adresini ve kullandığınız hizmet sağlayıcılarını kliniğinizin işleyişine göre kontrol edin.</p>
            </section>
          </aside>
        </div>
      )}
    </section>
  );
}
