import { useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import styles from './SettingsPage.module.css';

export function NotificationsPanel() {
  const navigate = useNavigate();

  return (
    <div className={styles.integrationCard}>
      <div className={styles.integrationHeader}>
        <div>
          <div className={styles.integrationName}>SMS / WhatsApp Bildirimleri</div>
          <div className={styles.integrationDesc}>
            Detaylı gönderim geçmişi, toplu kampanya ve mesaj şablonları artık Sms &amp; Whatsapp sayfasında.
          </div>
        </div>
      </div>
      <Button variant="primary" onClick={() => navigate('/sms-whatsapp')}>
        Sms &amp; Whatsapp sayfasına git
      </Button>
    </div>
  );
}
