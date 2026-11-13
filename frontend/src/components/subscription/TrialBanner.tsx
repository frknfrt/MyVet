import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useSubscriptionAccess } from '../../auth/SubscriptionAccessContext';
import styles from './TrialBanner.module.css';

/** Son kaç gün kala bant uyarı tonuna geçer. */
const WARNING_DAYS = 2;

/**
 * Deneme süresince panelin üstünde kalan gün bilgisini gösterir. Kural tabanlı bir durum
 * bildirimi olduğu için AI mavisini kullanmaz: normalde nötr, son günlerde warning tonu.
 */
export function TrialBanner() {
  const { session } = useAuth();
  const { access } = useSubscriptionAccess();

  if (!access || access.state !== 'TRIAL' || access.daysLeft === null) {
    return null;
  }

  const daysLeft = access.daysLeft;
  const tone = daysLeft <= WARNING_DAYS ? 'warning' : 'neutral';
  const message = daysLeft > 0 ? `Denemenizin bitmesine ${daysLeft} gün kaldı.` : 'Denemeniz bugün sona eriyor.';

  return (
    <div className={[styles.banner, styles[tone]].join(' ')} data-testid="trial-banner" data-tone={tone} role="status">
      <span>{message}</span>
      {session?.role === 'ADMIN' && (
        <Link to="/ayarlar/abonelik" className={styles.link}>
          Üyelik Başlat
        </Link>
      )}
    </div>
  );
}
