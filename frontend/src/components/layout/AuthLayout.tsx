import { ReactNode } from 'react';
import vetlyIcon from '../../assets/vetly-icon.png';
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
          <img className={styles.glyph} src={vetlyIcon} alt="" />
          <span className={styles.word}>Vetly</span>
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
