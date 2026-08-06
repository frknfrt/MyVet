import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { boardingApi, BoardingRoom, BoardingStay } from '../../api/boardingApi';
import { Button } from '../../components/ui/Button';
import { InvoiceDetailModal } from '../finance/InvoiceDetailModal';
import { BoardingStayStatusBadge } from './boardingStatus';
import { NewRoomModal } from './NewRoomModal';
import styles from './KonaklamaPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function isoToday() {
  return new Date().toISOString().slice(0, 10);
}

export function KonaklamaPage() {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState<BoardingRoom[]>([]);
  const [stays, setStays] = useState<BoardingStay[]>([]);
  const [loading, setLoading] = useState(true);
  const [roomModalOpen, setRoomModalOpen] = useState(false);
  const [invoiceId, setInvoiceId] = useState<string | null>(null);
  const [busyStayId, setBusyStayId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    Promise.all([boardingApi.listRooms(), boardingApi.listStays()])
      .then(([r, s]) => {
        setRooms(r);
        setStays(s);
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
  }, []);

  const activeStayByRoom = useMemo(() => {
    const map = new Map<string, BoardingStay>();
    stays.forEach((s) => {
      if (s.status === 'CHECKED_IN') map.set(s.roomId, s);
    });
    return map;
  }, [stays]);

  const groups = useMemo(() => {
    const map = new Map<string, BoardingRoom[]>();
    rooms
      .filter((r) => r.active)
      .forEach((r) => {
        const list = map.get(r.groupName) ?? [];
        list.push(r);
        map.set(r.groupName, list);
      });
    return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0], 'tr'));
  }, [rooms]);

  const kpis = useMemo(() => {
    const today = isoToday();
    const activeStays = stays.filter((s) => s.status === 'CHECKED_IN');
    return {
      active: activeStays.length,
      checkInToday: activeStays.filter((s) => s.checkInDate === today).length,
      checkOutToday: activeStays.filter((s) => s.expectedCheckOutDate === today).length,
      emptyRooms: rooms.filter((r) => r.active && !activeStayByRoom.has(r.id)).length,
    };
  }, [stays, rooms, activeStayByRoom]);

  async function handleCheckOut(stayId: string) {
    if (busyStayId) return;
    if (!window.confirm('Bu konaklama için çıkış işlemi yapılsın mı?')) return;
    setBusyStayId(stayId);
    setError(null);
    try {
      await boardingApi.checkOut(stayId, isoToday());
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusyStayId(null);
    }
  }

  async function handleCancel(stayId: string) {
    if (busyStayId) return;
    if (!window.confirm('Bu konaklama kaydı iptal edilsin mi?')) return;
    setBusyStayId(stayId);
    setError(null);
    try {
      await boardingApi.cancel(stayId);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusyStayId(null);
    }
  }

  async function handleOpenInvoice(stayId: string) {
    try {
      const id = await boardingApi.getInvoiceIdForStay(stayId);
      setInvoiceId(id);
    } catch (err) {
      setError(errorMessageOf(err));
    }
  }

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>Konaklama</h1>
          <div className={styles.sub}>Pansiyon odaları ve aktif konaklamalar</div>
        </div>
        <div className={styles.topbarActions}>
          <Button variant="secondary" onClick={() => setRoomModalOpen(true)}>
            + Oda Ekle
          </Button>
          <Button variant="primary" onClick={() => navigate('/konaklama/yeni')}>
            + Yeni Konaklama Kaydı
          </Button>
        </div>
      </div>

      <div className={styles.kpiRow}>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconActive}`}>●</div>
          <div>
            <div className={styles.kpiValue}>{kpis.active}</div>
            <div className={styles.kpiLabel}>Aktif Konaklama</div>
          </div>
        </div>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconIn}`}>→</div>
          <div>
            <div className={styles.kpiValue}>{kpis.checkInToday}</div>
            <div className={styles.kpiLabel}>Bugün Giriş</div>
          </div>
        </div>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconOut}`}>←</div>
          <div>
            <div className={styles.kpiValue}>{kpis.checkOutToday}</div>
            <div className={styles.kpiLabel}>Bugün Çıkış</div>
          </div>
        </div>
        <div className={styles.kpiCard}>
          <div className={`${styles.kpiIcon} ${styles.kpiIconEmpty}`}>○</div>
          <div>
            <div className={styles.kpiValue}>{kpis.emptyRooms}</div>
            <div className={styles.kpiLabel}>Boş Oda</div>
          </div>
        </div>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      {loading ? (
        <div className={styles.empty}>Yükleniyor...</div>
      ) : rooms.length === 0 ? (
        <div className={styles.emptyBig}>
          <div className={styles.emptyIcon}>+</div>
          <div>Henüz konaklama odası tanımlanmadı</div>
          <div className={styles.emptySub}>Kliniğinize uygun oda/kafes kategorilerini ekleyerek başlayın</div>
          <Button variant="secondary" onClick={() => setRoomModalOpen(true)}>
            Oda Ekle
          </Button>
        </div>
      ) : (
        groups.map(([groupName, groupRooms]) => (
          <div key={groupName} className={styles.groupSection}>
            <div className={styles.groupTitle}>{groupName}</div>
            <div className={styles.roomGrid}>
              {groupRooms.map((room) => {
                const stay = activeStayByRoom.get(room.id);
                return (
                  <div key={room.id} className={`${styles.roomCard} ${stay ? styles.roomCardOccupied : styles.roomCardEmpty}`}>
                    <div className={styles.roomCardHead}>
                      <span className={styles.roomName}>{room.name}</span>
                      {stay ? (
                        <BoardingStayStatusBadge status={stay.status} />
                      ) : (
                        <span className={styles.roomCapacity}>Kapasite: {room.capacity}</span>
                      )}
                    </div>
                    {stay ? (
                      <>
                        <div className={styles.stayPatient}>{stay.patientName}</div>
                        <div className={styles.stayOwner}>{stay.ownerName}</div>
                        <div className={styles.stayDates}>
                          {new Date(stay.checkInDate).toLocaleDateString('tr-TR')}
                          {stay.expectedCheckOutDate && ` → ${new Date(stay.expectedCheckOutDate).toLocaleDateString('tr-TR')}`}
                        </div>
                        <div className={styles.roomCardActions}>
                          <button
                            type="button"
                            className={styles.actionBtn}
                            onClick={() => navigate(`/hastalar/${stay.patientId}`)}
                          >
                            Hasta
                          </button>
                          <button type="button" className={styles.actionBtn} onClick={() => handleOpenInvoice(stay.id)}>
                            Fatura
                          </button>
                          <button
                            type="button"
                            className={styles.actionBtn}
                            disabled={busyStayId === stay.id}
                            onClick={() => handleCheckOut(stay.id)}
                          >
                            Çıkış Yap
                          </button>
                          <button
                            type="button"
                            className={`${styles.actionBtn} ${styles.actionBtnDanger}`}
                            disabled={busyStayId === stay.id}
                            onClick={() => handleCancel(stay.id)}
                          >
                            İptal
                          </button>
                        </div>
                      </>
                    ) : (
                      <button
                        type="button"
                        className={styles.emptyRoomLink}
                        onClick={() => navigate(`/konaklama/yeni?roomId=${room.id}`)}
                      >
                        + Konaklama Kaydı Oluştur
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        ))
      )}

      <NewRoomModal open={roomModalOpen} onClose={() => setRoomModalOpen(false)} onCreated={load} />
      <InvoiceDetailModal invoiceId={invoiceId} onClose={() => setInvoiceId(null)} onChanged={load} />
    </AppShell>
  );
}
