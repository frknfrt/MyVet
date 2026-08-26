import { useNavigate } from 'react-router-dom';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import styles from './AiCenterPanel.module.css';

interface CapabilityCardProps {
  title: string;
  description: string;
  badge: { label: string; tone: 'ai' | 'warning' | 'neutral' };
  actionLabel: string;
  onAction: () => void;
}

function CapabilityCard({ title, description, badge, actionLabel, onAction }: CapabilityCardProps) {
  return (
    <Card className={styles.card}>
      <div className={styles.cardHeader}>
        <h3 className={styles.cardTitle}>{title}</h3>
        <Badge tone={badge.tone}>{badge.label}</Badge>
      </div>
      <p className={styles.cardDesc}>{description}</p>
      <Button variant="secondary" onClick={onAction}>
        {actionLabel}
      </Button>
    </Card>
  );
}

export function AiCenterPanel() {
  const navigate = useNavigate();

  return (
    <div>
      <div className={styles.intro}>
        Kliniğinizde aktif olan yapay zekâ destekli ve kural tabanlı karar destek özellikleri — hepsi hekim onayı
        bekler, hiçbiri sessizce otomatik uygulanmaz.
      </div>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Yapay Zekâ Destekli</h2>
        <div className={styles.grid}>
          <CapabilityCard
            title="Sesli SOAP Dikte + AI Taslağı"
            description="Muayene sırasında konuşmayı tarayıcı üzerinden metne çevirir, ardından SOAP alanlarına taslak olarak dağıtır. Hekim taslağı düzenleyip onaylamadan kaydedilmez."
            badge={{ label: 'Aktif — Taslak Adaptör', tone: 'ai' }}
            actionLabel="Bir muayenede kullan"
            onAction={() => navigate('/randevu')}
          />
          <CapabilityCard
            title="Tanı Desteği / Tedavi Önerisi"
            description="Hasta geçmişi ve semptomlara göre olası tanı/tedavi önerisi. Gerçek bir dil modeli entegrasyonu ve güvenilir bir tıbbi referans kaynağı olmadan uydurma veri üretilmeyecek."
            badge={{ label: 'Planlanıyor', tone: 'neutral' }}
            actionLabel="Yakında"
            onAction={() => {}}
          />
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Kural Tabanlı Kontroller (AI değil)</h2>
        <div className={styles.sectionNote}>
          Bu kontroller bir dil modeli kullanmaz — kliniğin/hekimin kendi girdiği referans veriyi çapraz kontrol eder.
          Bu yüzden tasarım sisteminde AI mavisi değil, uyarı (rust/terrakota) tonuyla gösterilirler.
        </div>
        <div className={styles.grid}>
          <CapabilityCard
            title="İlaç Etkileşim Kontrolü"
            description="Reçeteye eklenen ilaçları, İlaç Kataloğu'nda işaretlenmiş etkileşim çiftlerine göre karşılaştırır ve hekime bilgilendirici bir uyarı gösterir — bloklayıcı değildir."
            badge={{ label: 'Aktif — Kural Tabanlı', tone: 'warning' }}
            actionLabel="İlaç Kataloğu'nu yönet"
            onAction={() => navigate('/ayarlar/ilac-katalogu')}
          />
          <CapabilityCard
            title="Laboratuvar Referans Aralığı Ön-Değerlendirmesi"
            description="Girilen lab sonuçlarını referans aralığıyla karşılaştırıp aralık dışı değerleri otomatik işaretler; nihai yorum hekime aittir."
            badge={{ label: 'Aktif — Kural Tabanlı', tone: 'warning' }}
            actionLabel="Laboratuvara git"
            onAction={() => navigate('/laboratuvar')}
          />
        </div>
      </section>
    </div>
  );
}
