import type { UsageItem, UsageSnapshot } from '../../api/subscriptionApi';
import styles from './UsageSummary.module.css';

const nf = new Intl.NumberFormat('tr-TR');

function Row({ label, item }: { label: string; item: UsageItem }) {
  if (item.quota === null) {
    return (
      <div className={styles.row}>
        <span className={styles.label}>{label}</span>
        <span>{nf.format(item.used)} (sınırsız)</span>
      </div>
    );
  }
  const over = item.used - item.quota;
  const pct = item.quota === 0 ? 100 : Math.min(100, Math.round((item.used / item.quota) * 100));
  return (
    <div className={styles.row}>
      <span className={styles.label}>{label}</span>
      <span>
        {nf.format(item.used)} / {nf.format(item.quota)}
        {over > 0 && <strong className={styles.over}> — aşım: {nf.format(over)}</strong>}
      </span>
      <div className={styles.bar} aria-hidden>
        <div className={over > 0 ? styles.fillOver : styles.fill} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

export function UsageSummary({ usage }: { usage: UsageSnapshot }) {
  return (
    <div className={styles.wrap}>
      <Row label="Bu ay AI" item={usage.ai} />
      <Row label="Bu ay mesaj (SMS + WhatsApp)" item={usage.message} />
      {!usage.enforced && <div className={styles.note}>Kotalar henüz uygulanmıyor; yalnızca sayılıyor.</div>}
    </div>
  );
}
