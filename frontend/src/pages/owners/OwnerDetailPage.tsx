import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { billingApi, InvoiceSummary } from '../../api/billingApi';
import { OwnerProfile, patientApi } from '../../api/patientApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { colorFor, initialsOf } from '../dashboard/avatarColor';
import { InvoiceDetailModal } from '../finance/InvoiceDetailModal';
import { InvoiceStatusBadge } from '../finance/invoiceStatus';
import { PatientStatusBadge } from '../patients/statusBadge';
import { ConsentTab } from './ConsentTab';
import { OwnerEditModal } from './OwnerEditModal';
import styles from './OwnerDetailPage.module.css';

type Tab = 'hastalar' | 'finans' | 'kvkk';

function ConsentBadge({ label, granted }: { label: string; granted: boolean }) {
  return <Badge tone={granted ? 'success' : 'neutral'}>{label}</Badge>;
}

export function OwnerDetailPage() {
  const { ownerId } = useParams<{ ownerId: string }>();
  const navigate = useNavigate();
  const { session } = useAuth();
  // OwnersController/PatientsController yazma -- VET/RECEPTIONIST/ADMIN (TECHNICIAN yok).
  const canWrite = session ? ['VET', 'RECEPTIONIST', 'ADMIN'].includes(session.role) : false;

  const [profile, setProfile] = useState<OwnerProfile | null>(null);
  const [tab, setTab] = useState<Tab>('hastalar');
  const [invoices, setInvoices] = useState<InvoiceSummary[] | null>(null);
  const [invoicesForbidden, setInvoicesForbidden] = useState(false);
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(null);
  const [editOpen, setEditOpen] = useState(false);

  function load() {
    if (!ownerId) return;
    patientApi.getOwnerProfile(ownerId).then(setProfile);
    billingApi
      .listInvoices()
      .then((all) => setInvoices(all.filter((inv) => inv.ownerId === ownerId)))
      .catch((err) => {
        if (err instanceof ApiError && err.status === 403) setInvoicesForbidden(true);
      });
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ownerId]);

  if (!ownerId) return null;

  if (!profile) {
    return (
      <AppShell>
        <div className={styles.loading}>Yükleniyor...</div>
      </AppShell>
    );
  }

  const outstanding = (invoices ?? [])
    .filter((i) => i.status !== 'PAID' && i.status !== 'VOID')
    .reduce((sum, i) => sum + i.totalAmount, 0);

  return (
    <AppShell>
      <button className={styles.backLink} onClick={() => navigate('/hastalar')}>
        ← Hastalar &amp; Sahipler
      </button>

      {profile.criticalAlert && <div className={styles.criticalAlert}>⚠ {profile.criticalAlert}</div>}

      <div className={styles.headerCard}>
        <div className={styles.headerTop}>
          <div className={styles.avatar} style={{ background: colorFor(profile.id) }}>
            {initialsOf(profile.fullName)}
          </div>
          <div className={styles.identity}>
            <div className={styles.nameRow}>
              <span className={styles.name}>
                {profile.fullName}
                {profile.middleName ? ` ${profile.middleName}` : ''}
              </span>
            </div>
            <div className={styles.consentRow}>
              <ConsentBadge label="SMS" granted={profile.smsConsent} />
              <ConsentBadge label="WhatsApp" granted={profile.whatsappConsent} />
              <ConsentBadge label="Bildirim" granted={profile.notificationConsent} />
              <ConsentBadge label="E-posta Pazarlama" granted={profile.marketingConsent} />
            </div>
          </div>
          <div className={styles.spacer} />
          {canWrite && (
            <div className={styles.headerActions}>
              <Button variant="secondary" onClick={() => setEditOpen(true)}>
                Düzenle
              </Button>
              <Button variant="primary" onClick={() => navigate(`/hastalar/yeni?ownerId=${ownerId}`)}>
                Yeni Hasta Ekle
              </Button>
            </div>
          )}
        </div>

        <div className={styles.infoGrid}>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>Telefon</div>
            <div className={styles.infoValue}>{profile.phone}</div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>İkincil Telefon</div>
            <div className={styles.infoValue}>{profile.secondaryPhone ?? '—'}</div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>E-posta</div>
            <div className={styles.infoValue}>{profile.email ?? '—'}</div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>Meslek</div>
            <div className={styles.infoValue}>{profile.occupation ?? '—'}</div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>Doğum Tarihi</div>
            <div className={styles.infoValue}>
              {profile.birthDate ? new Date(profile.birthDate).toLocaleDateString('tr-TR') : '—'}
            </div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>TC Kimlik No</div>
            <div className={styles.infoValue}>{profile.nationalId ?? '—'}</div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>Adres</div>
            <div className={styles.infoValue}>
              {[profile.address, profile.district, profile.city].filter(Boolean).join(', ') || '—'}
            </div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>Referans Kaynağı</div>
            <div className={styles.infoValue}>{profile.referralSource ?? '—'}</div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>Müşteri İndirimi</div>
            <div className={styles.infoValue}>{profile.clientDiscount > 0 ? `%${profile.clientDiscount}` : '—'}</div>
          </div>
          <div className={styles.infoItem}>
            <div className={styles.infoLabel}>Protokol No</div>
            <div className={styles.infoValue}>{profile.protocolNumber ?? '—'}</div>
          </div>
        </div>

        {profile.notes && (
          <div className={styles.notesBlock}>
            <div className={styles.infoLabel}>Notlar</div>
            <div className={styles.notesText}>{profile.notes}</div>
          </div>
        )}
      </div>

      <div className={styles.tabs}>
        <div className={`${styles.tab} ${tab === 'hastalar' ? styles.tabActive : ''}`} onClick={() => setTab('hastalar')}>
          Hastaları ({profile.patients.length})
        </div>
        <div className={`${styles.tab} ${tab === 'finans' ? styles.tabActive : ''}`} onClick={() => setTab('finans')}>
          Finans
        </div>
        <div className={`${styles.tab} ${tab === 'kvkk' ? styles.tabActive : ''}`} onClick={() => setTab('kvkk')}>
          KVKK
        </div>
      </div>

      {tab === 'hastalar' && (
        <div className={styles.tableCard}>
          {profile.patients.length === 0 ? (
            <div className={styles.empty}>Kayıtlı hasta yok</div>
          ) : (
            profile.patients.map((p) => (
              <div key={p.id} className={styles.patientRow} onClick={() => navigate(`/hastalar/${p.id}`)}>
                <div className={styles.patientAvatar} style={{ background: colorFor(p.id) }}>
                  {initialsOf(p.name)}
                </div>
                <div className={styles.patientName}>{p.name}</div>
                <div className={styles.muted}>{[p.speciesName, p.breedName].filter(Boolean).join(' · ') || '—'}</div>
                <div className={styles.spacer} />
                <PatientStatusBadge status={p.status} />
              </div>
            ))
          )}
        </div>
      )}

      {tab === 'finans' && (
        <div className={styles.tableCard}>
          {invoicesForbidden ? (
            <div className={styles.empty}>Bu bölümü görüntüleme yetkiniz yok</div>
          ) : (
            <>
              {invoices !== null && (
                <div className={styles.balanceBar}>
                  <span>Açık Bakiye</span>
                  <strong>{outstanding.toFixed(2)} ₺</strong>
                </div>
              )}
              <div className={`${styles.tableHead} ${styles.invoicesHead}`}>
                <div>Kesim Tarihi</div>
                <div>Tutar</div>
                <div>Durum</div>
              </div>
              {invoices === null ? (
                <div className={styles.empty}>Yükleniyor...</div>
              ) : invoices.length === 0 ? (
                <div className={styles.empty}>Fatura bulunmuyor</div>
              ) : (
                invoices.map((inv) => (
                  <div
                    key={inv.id}
                    className={`${styles.row} ${styles.invoicesRow}`}
                    onClick={() => setSelectedInvoiceId(inv.id)}
                  >
                    <div className={styles.muted}>{inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString('tr-TR') : 'Taslak'}</div>
                    <div className={styles.amount}>{inv.totalAmount.toFixed(2)} ₺</div>
                    <div>
                      <InvoiceStatusBadge status={inv.status} />
                    </div>
                  </div>
                ))
              )}
            </>
          )}
        </div>
      )}

      {tab === 'kvkk' && <ConsentTab ownerId={ownerId} />}

      <InvoiceDetailModal invoiceId={selectedInvoiceId} onClose={() => setSelectedInvoiceId(null)} onChanged={load} />
      <OwnerEditModal
        open={editOpen}
        profile={profile}
        onClose={() => setEditOpen(false)}
        onSaved={() => {
          setEditOpen(false);
          load();
        }}
      />
    </AppShell>
  );
}
