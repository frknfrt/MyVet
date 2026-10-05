import { FormEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { Coupon, CouponDiscountType, platformAdminApi } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './PlatformAdminPages.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function formatDiscount(coupon: Coupon): string {
  return coupon.discountType === 'PERCENTAGE' ? `%${coupon.discountValue}` : `${coupon.discountValue.toLocaleString('tr-TR')} ₺`;
}

function formatDate(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString('tr-TR');
}

interface CouponFormState {
  code: string;
  discountType: CouponDiscountType;
  discountValue: string;
  maxRedemptions: string;
  expiresAt: string;
}

const EMPTY_FORM: CouponFormState = {
  code: '',
  discountType: 'PERCENTAGE',
  discountValue: '',
  maxRedemptions: '',
  expiresAt: '',
};

export function CouponsPage() {
  const [coupons, setCoupons] = useState<Coupon[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<CouponFormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set());
  const initialFormRef = useRef<CouponFormState>(EMPTY_FORM);

  function load() {
    setLoading(true);
    platformAdminApi
      .listCoupons()
      .then(setCoupons)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function openCreate() {
    setForm(EMPTY_FORM);
    initialFormRef.current = EMPTY_FORM;
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await platformAdminApi.createCoupon({
        code: form.code,
        discountType: form.discountType,
        discountValue: Number(form.discountValue),
        maxRedemptions: form.maxRedemptions === '' ? null : Number(form.maxRedemptions),
        expiresAt: form.expiresAt === '' ? null : form.expiresAt,
      });
      setModalOpen(false);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(coupon: Coupon) {
    setTogglingIds((prev) => new Set(prev).add(coupon.id));
    setError(null);
    try {
      if (coupon.active) {
        await platformAdminApi.deactivateCoupon(coupon.id);
      } else {
        await platformAdminApi.activateCoupon(coupon.id);
      }
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setTogglingIds((prev) => {
        const next = new Set(prev);
        next.delete(coupon.id);
        return next;
      });
    }
  }

  return (
    <div>
      <div className={styles.title}>Kuponlar</div>
      <p className={styles.note}>
        vetly.com'daki kayıt akışında, ilk ödemeye indirim uygulamak için kullanılabilecek kodlar. Bir kupon sadece
        kayıt sırasındaki ilk faturayı indirimli yapar, aylık tekrar eden faturalara yansımaz. "Potansiyel
        Müşteriler" sayfasından takip ettiğiniz bekleyen kayıtlara özel bir teklif sunmak için de kullanılabilir.
      </p>

      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={openCreate}>
          + Yeni Kupon
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.tableCard}>
        <div className={`${styles.tableHead} ${styles.couponRow}`}>
          <div>Kod</div>
          <div>İndirim</div>
          <div>Kullanım</div>
          <div>Son Kullanma</div>
          <div>Durum</div>
          <div></div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : coupons.length === 0 ? (
          <div className={styles.empty}>Henüz kupon tanımlanmadı</div>
        ) : (
          coupons.map((coupon) => (
            <div key={coupon.id} className={`${styles.row} ${styles.couponRow}`} style={{ cursor: 'default' }}>
              <div>{coupon.code}</div>
              <div className={styles.muted}>{formatDiscount(coupon)}</div>
              <div className={styles.muted}>
                {coupon.redemptionCount}
                {coupon.maxRedemptions !== null ? ` / ${coupon.maxRedemptions}` : ''}
              </div>
              <div className={styles.muted}>{formatDate(coupon.expiresAt)}</div>
              <div>
                <Badge tone={coupon.active ? (coupon.redeemable ? 'success' : 'warning') : 'neutral'}>
                  {coupon.active ? (coupon.redeemable ? 'Aktif' : 'Tükendi/Süresi Doldu') : 'Pasif'}
                </Badge>
              </div>
              <div className={styles.rowActions}>
                <Button
                  variant="secondary"
                  onClick={() => toggleActive(coupon)}
                  disabled={togglingIds.has(coupon.id)}
                >
                  {coupon.active ? 'Pasife Al' : 'Aktif Et'}
                </Button>
              </div>
            </div>
          ))
        )}
      </div>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        width={440}
        dirty={JSON.stringify(form) !== JSON.stringify(initialFormRef.current)}
      >
        <form onSubmit={handleSubmit}>
          <div className={styles.modalTitle}>Yeni Kupon</div>

          <FieldWrap label="Kupon Kodu">
            <Input
              value={form.code}
              onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
              placeholder="ör. HOSGELDIN10"
              required
            />
          </FieldWrap>
          <FieldWrap label="İndirim Türü">
            <select
              value={form.discountType}
              onChange={(e) => setForm((f) => ({ ...f, discountType: e.target.value as CouponDiscountType }))}
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: 8,
                border: '1px solid var(--color-border)',
                fontSize: 13,
              }}
            >
              <option value="PERCENTAGE">Yüzde (%)</option>
              <option value="FIXED_AMOUNT">Sabit Tutar (₺)</option>
            </select>
          </FieldWrap>
          <FieldWrap label={form.discountType === 'PERCENTAGE' ? 'İndirim Yüzdesi' : 'İndirim Tutarı (₺)'}>
            <Input
              type="number"
              min={0}
              step="0.01"
              value={form.discountValue}
              onChange={(e) => setForm((f) => ({ ...f, discountValue: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Maksimum Kullanım (opsiyonel)">
            <Input
              type="number"
              min={1}
              step="1"
              value={form.maxRedemptions}
              onChange={(e) => setForm((f) => ({ ...f, maxRedemptions: e.target.value }))}
              placeholder="Boş bırakılırsa sınırsız"
            />
          </FieldWrap>
          <FieldWrap label="Son Kullanma Tarihi (opsiyonel)">
            <Input
              type="date"
              value={form.expiresAt}
              onChange={(e) => setForm((f) => ({ ...f, expiresAt: e.target.value }))}
            />
          </FieldWrap>

          <div className={styles.modalActions}>
            <Button type="button" variant="secondary" onClick={() => setModalOpen(false)}>
              Vazgeç
            </Button>
            <Button type="submit" variant="primary" disabled={saving}>
              {saving ? 'Kaydediliyor...' : 'Kaydet'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
