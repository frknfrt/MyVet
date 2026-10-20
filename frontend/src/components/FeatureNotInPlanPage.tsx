import { Link } from 'react-router-dom';
import { AppShell } from './layout/AppShell';
import { Card } from './ui/Card';

export function FeatureNotInPlanPage() {
  return (
    <AppShell>
      <Card style={{ maxWidth: 520, margin: '48px auto', padding: 24, textAlign: 'center' }}>
        <h2 style={{ marginTop: 0 }}>Bu modül paketinizde yok</h2>
        <p>
          Bu modülü kullanmak için paketinizi yükseltebilirsiniz:{' '}
          <Link to="/ayarlar/abonelik">Ayarlar › Abonelik</Link>
        </p>
      </Card>
    </AppShell>
  );
}
