import { ReactNode } from 'react';
import styles from './AuthLayout.module.css';

interface AuthLayoutProps {
  headline: ReactNode;
  subcopy: string;
  children: ReactNode;
}

/**
 * Login, Klinik Kaydi ve Kurulum Sihirbazi sayfalarinin ortak split-screen
 * kabugu. Sidebar navConfig'teki "tek kaynak" prensibinin auth akisi
 * karsiligi: marka paneli her yerde BIREBIR AYNI, kopyalanmaz.
 */
export function AuthLayout({ headline, subcopy, children }: AuthLayoutProps) {
  return (
    <div className={styles.shell}>
      <div className={styles.brandPanel}>
        <div className={styles.brandMark}>
          <div className={styles.glyph}>
            <svg viewBox="0 0 24 24" fill="none" stroke="#180F24" strokeWidth={2.2} strokeLinecap="round">
              <path d="M12 3v6M12 15v6M4.2 7.5l5.2 3M14.6 13.5l5.2 3M4.2 16.5l5.2-3M14.6 10.5l5.2-3" />
            </svg>
          </div>
          <span className={styles.word}>MyVet</span>
        </div>
        <h1 className={styles.headline}>{headline}</h1>
        <p className={styles.subcopy}>{subcopy}</p>
      </div>

      <div className={styles.formPanel}>
        <div className={styles.formCard}>{children}</div>
      </div>
    </div>
  );
}
