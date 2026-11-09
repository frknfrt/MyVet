import { useState } from 'react';
import { Modal } from '../components/ui/Modal';
import { Button } from '../components/ui/Button';
import styles from './PrivacyNoticeLink.module.css';

interface Props {
  text: string;
  version: number;
  label?: string;
  variant?: 'secondary' | 'tertiary';
  className?: string;
}

export function PrivacyNoticeLink({ text, version, label = 'KVKK Aydınlatma Metni', variant = 'tertiary', className }: Props) {
  const [open,setOpen] = useState(false);
  return <>
    <Button type="button" variant={variant} className={className} onClick={() => setOpen(true)}>{label}</Button>
    <Modal open={open} onClose={() => setOpen(false)} width={760}>
      <div className={styles.header}>
        <span className={styles.eyebrow}>BİLGİLENDİRME</span>
        <h2>KVKK Aydınlatma Metni</h2>
        <span className={styles.version}>Sürüm {version}</span>
      </div>
      <div className={styles.content}>
        {text.split(/\n\s*\n/).map((paragraph,index) => <p key={index}>{paragraph}</p>)}
      </div>
      <div className={styles.footer}>
        <Button type="button" variant="secondary" onClick={() => setOpen(false)}>Metni kapat</Button>
      </div>
    </Modal>
  </>;
}
