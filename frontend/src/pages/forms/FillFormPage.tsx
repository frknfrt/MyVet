import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { blobToBase64, formsApi, type FormTemplateDetail } from '../../api/formsApi';
import { patientApi, type OwnerProfile, type PatientProfile } from '../../api/patientApi';
import { computeWriteBackDiff, initialValues, resolveBindingValues, type SignerType, type WriteBackRow } from '../../forms/bindings';
import { FormRenderer } from '../../forms/FormRenderer';
import type { FormItem, FormValue, FormValues } from '../../forms/formModel';
import { SignaturePad } from '../../forms/SignaturePad';
import { isValidTcKimlik } from '../owners/tcKimlik';
import styles from './FillFormPage.module.css';

type Step = 'form' | 'confirm' | 'done';

interface Loaded {
  template: FormTemplateDetail;
  patient: PatientProfile;
  owner: OwnerProfile;
}

function errMessage(e: unknown, fallback: string): string {
  return e instanceof Error && e.message ? e.message : fallback;
}

function isEmptyValue(v: FormValue | undefined): boolean {
  return v === undefined || (Array.isArray(v) ? v.length === 0 : v.trim() === '');
}

function allFieldItems(loaded: Loaded): FormItem[] {
  return loaded.template.content.sections
    .flatMap((s) => s.items)
    .filter((i) => i.code && i.type !== 'TEXT_BLOCK' && i.type !== 'SIGNATURE');
}

/** Hata verilen alana kaydirir (FormRenderer id/name sonu "-kod" ile biter). */
function scrollToField(code: string) {
  const el = document.querySelector(`[id$="-${code}"], [name$="-${code}"]`);
  (el as HTMLElement | null)?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
}

export function FillFormPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const uid = useId();
  const templateId = params.get('templateId');
  const patientId = params.get('patientId');
  const encounterId = params.get('encounterId');

  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [loadError, setLoadError] = useState<string | null>(
    templateId && patientId ? null : 'Form açılamadı: şablon veya hasta bilgisi eksik.',
  );
  const [values, setValues] = useState<FormValues>({});
  const [dirty, setDirty] = useState(false);
  const [signerKind, setSignerKind] = useState<SignerType>('OWNER');
  const [otherName, setOtherName] = useState('');
  const [otherTc, setOtherTc] = useState('');
  const [otherPhone, setOtherPhone] = useState('');
  const [signature, setSignature] = useState<Blob | null>(null);
  const [step, setStep] = useState<Step>('form');
  const [busy, setBusy] = useState(false);
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [signatureError, setSignatureError] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);
  const [tcError, setTcError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [rows, setRows] = useState<WriteBackRow[]>([]);
  const [accepted, setAccepted] = useState<Record<string, boolean>>({});
  const [signedId, setSignedId] = useState<string | null>(null);
  const [writeBackNotice, setWriteBackNotice] = useState<string | null>(null);
  const [pdfError, setPdfError] = useState<string | null>(null);
  const errorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!templateId || !patientId) return;
    let cancelled = false;
    (async () => {
      try {
        const [template, patient] = await Promise.all([formsApi.getTemplate(templateId), patientApi.getProfile(patientId)]);
        const owner = await patientApi.getOwnerProfile(patient.ownerId);
        if (cancelled) return;
        const resolved = resolveBindingValues({ patient, owner, clinicName: template.clinicName, today: new Date() });
        setValues(initialValues(template.content, resolved));
        setLoaded({ template, patient, owner });
      } catch (e) {
        if (!cancelled) setLoadError(`Form açılamadı: ${errMessage(e, 'Bilgiler yüklenemedi.')}`);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [templateId, patientId]);

  const resolved = useMemo(
    () =>
      loaded
        ? resolveBindingValues({ patient: loaded.patient, owner: loaded.owner, clinicName: loaded.template.clinicName, today: new Date() })
        : null,
    [loaded],
  );

  const goBack = () => {
    if (location.key !== 'default') navigate(-1);
    else navigate(patientId ? `/hastalar/${patientId}` : '/panel');
  };

  const close = () => {
    if (step !== 'done' && (dirty || signature) && !window.confirm('Doldurduğunuz bilgiler kaybolacak. Kapatılsın mı?')) return;
    goBack();
  };

  if (loadError) {
    return (
      <div className={styles.page}>
        <div className={styles.centerBox}>
          <p className={styles.errorText}>{loadError}</p>
          <button type="button" className={styles.secondary} onClick={goBack}>Geri</button>
        </div>
      </div>
    );
  }
  if (!loaded || !resolved) {
    return (
      <div className={styles.page}>
        <div className={styles.centerBox}>Yükleniyor…</div>
      </div>
    );
  }

  const { template, owner, patient } = loaded;

  const onChange = (code: string, value: FormValue) => {
    setValues((prev) => ({ ...prev, [code]: value }));
    setDirty(true);
  };

  const signerPayload = () =>
    signerKind === 'OWNER'
      ? { type: 'OWNER' as const, name: owner.fullName, nationalId: null, phone: null }
      : {
          type: 'OTHER' as const,
          name: otherName.trim(),
          nationalId: otherTc.trim() || null,
          phone: otherPhone.trim() || null,
        };

  const validate = (): boolean => {
    const missing = allFieldItems(loaded).filter((i) => i.required && isEmptyValue(values[i.code ?? '']));
    setFormErrors(missing.map((i) => `${i.label} alanı zorunlu`));
    const sigErr = signature ? null : 'Lütfen imza atın';
    setSignatureError(sigErr);
    let nErr: string | null = null;
    let tErr: string | null = null;
    if (signerKind === 'OTHER') {
      if (!otherName.trim()) nErr = 'Ad Soyad zorunlu';
      if (otherTc.trim() && !isValidTcKimlik(otherTc.trim())) tErr = 'Geçersiz TC kimlik no';
    }
    setNameError(nErr);
    setTcError(tErr);
    const ok = missing.length === 0 && !sigErr && !nErr && !tErr;
    if (!ok) {
      requestAnimationFrame(() => {
        if (missing.length > 0 && missing[0].code) scrollToField(missing[0].code);
        else errorRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      });
    }
    return ok;
  };

  const submit = async () => {
    if (busy) return;
    setSubmitError(null);
    if (!validate()) return;
    const diff = computeWriteBackDiff(template.content, values, resolved, signerKind);
    if (diff.length > 0) {
      setRows(diff);
      setAccepted(Object.fromEntries(diff.map((r) => [r.binding, r.valid])));
      setStep('confirm');
      window.scrollTo?.({ top: 0 });
      return;
    }
    await send([]);
  };

  const send = async (selected: WriteBackRow[]) => {
    if (busy || !signature) return;
    setBusy(true);
    setSubmitError(null);
    let id: string;
    try {
      const signaturePngBase64 = await blobToBase64(signature);
      id = await formsApi.createSignedForm({
        templateId: template.id,
        patientId: patient.id,
        encounterId: encounterId ?? null,
        values,
        signer: signerPayload(),
        signaturePngBase64,
      });
    } catch (e) {
      setSubmitError(errMessage(e, 'Form kaydedilemedi.'));
      setStep('form');
      setBusy(false);
      return;
    }
    setSignedId(id);
    if (selected.length > 0) {
      try {
        const result = await formsApi.applyWriteBacks({
          ownerId: owner.id,
          patientId: patient.id,
          signedFormId: id,
          items: selected.map((r) => ({ binding: r.binding, value: r.newValue })),
        });
        if (result.skipped.length > 0) {
          setWriteBackNotice(
            `Form kaydedildi ama bazı bilgiler kayda yazılamadı: ${result.skipped.map((s) => `${s.binding} (${s.reason})`).join(', ')}`,
          );
        }
      } catch (e) {
        setWriteBackNotice(`Form kaydedildi ama bazı bilgiler kayda yazılamadı: ${errMessage(e, 'bilinmeyen hata')}`);
      }
    }
    setStep('done');
    setBusy(false);
  };

  const downloadPdf = async () => {
    if (!signedId) return;
    setPdfError(null);
    try {
      const res = await formsApi.fetchFile(formsApi.signedFormPdfUrl(signedId));
      if (!res.ok) throw new Error('PDF indirilemedi.');
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = url;
      a.download = `${template.title}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
    } catch (e) {
      setPdfError(errMessage(e, 'PDF indirilemedi.'));
    }
  };

  const topBar = (
    <header className={styles.topBar}>
      <div className={styles.topText}>
        <span className={styles.clinic}>{template.clinicName}</span>
        <span className={styles.formTitle}>{template.title}</span>
      </div>
      <button type="button" className={styles.secondary} disabled={busy} onClick={close}>Kapat</button>
    </header>
  );

  if (step === 'done') {
    return (
      <div className={styles.page}>
        {topBar}
        <main className={styles.body}>
          <div className={styles.doneBox}>
            <h2 className={styles.doneTitle}>Teşekkürler, form kaydedildi.</h2>
            {writeBackNotice && <div className={styles.warning} role="status">{writeBackNotice}</div>}
            {pdfError && <div className={styles.error} role="alert">{pdfError}</div>}
            <div className={styles.actions}>
              <button type="button" className={styles.primary} onClick={downloadPdf}>PDF indir</button>
              <button type="button" className={styles.secondary} onClick={goBack}>Kapat</button>
            </div>
          </div>
        </main>
      </div>
    );
  }

  if (step === 'confirm') {
    const selected = rows.filter((r) => r.valid && accepted[r.binding]);
    return (
      <div className={styles.page}>
        {topBar}
        <main className={styles.body}>
          <h2 className={styles.confirmTitle}>Kaydınız da güncellensin mi?</h2>
          <p className={styles.hint}>Formda girilen bilgiler kayıttakinden farklı. Güncellemek istediklerinizi seçin.</p>
          <ul className={styles.rowList}>
            {rows.map((r) => (
              <li key={r.binding} className={styles.row}>
                <div className={styles.rowText}>
                  <strong>{r.label}:</strong> {r.oldValue || '—'} → {r.newValue}
                  {!r.valid && <div className={styles.rowReason}>{r.reason}</div>}
                </div>
                <div className={styles.toggle} role="radiogroup" aria-label={r.label}>
                  <label className={styles.toggleOpt}>
                    <input
                      type="radio"
                      name={`${uid}-${r.binding}`}
                      disabled={!r.valid}
                      checked={r.valid && accepted[r.binding]}
                      onChange={() => setAccepted((a) => ({ ...a, [r.binding]: true }))}
                    />
                    <span>Evet</span>
                  </label>
                  <label className={styles.toggleOpt}>
                    <input
                      type="radio"
                      name={`${uid}-${r.binding}`}
                      disabled={!r.valid}
                      checked={!r.valid || !accepted[r.binding]}
                      onChange={() => setAccepted((a) => ({ ...a, [r.binding]: false }))}
                    />
                    <span>Hayır</span>
                  </label>
                </div>
              </li>
            ))}
          </ul>
          {submitError && <div className={styles.error} role="alert">{submitError}</div>}
          <div className={styles.actions}>
            <button type="button" className={styles.secondary} disabled={busy} onClick={() => setStep('form')}>Geri</button>
            <button type="button" className={styles.primary} disabled={busy} onClick={() => send(selected)}>
              {busy ? 'Gönderiliyor…' : 'Gönder'}
            </button>
          </div>
        </main>
      </div>
    );
  }

  const signatureSection = (
    <div className={styles.signBox}>
      <fieldset className={styles.signer}>
        <legend className={styles.signLegend}>İmzalayan</legend>
        <label className={styles.signerOpt}>
          <input type="radio" name={`${uid}-signer`} checked={signerKind === 'OWNER'} onChange={() => setSignerKind('OWNER')} />
          <span>Hasta sahibi — {owner.fullName}</span>
        </label>
        <label className={styles.signerOpt}>
          <input type="radio" name={`${uid}-signer`} checked={signerKind === 'OTHER'} onChange={() => setSignerKind('OTHER')} />
          <span>Başka biri imzalıyor</span>
        </label>
      </fieldset>
      {signerKind === 'OTHER' && (
        <div className={styles.otherFields}>
          <div className={styles.field}>
            <label htmlFor={`${uid}-oname`} className={styles.label}>Ad Soyad <span className={styles.req}>*</span></label>
            <input id={`${uid}-oname`} className={styles.input} value={otherName} onChange={(e) => { setOtherName(e.target.value); setDirty(true); }} />
            {nameError && <span className={styles.fieldError}>{nameError}</span>}
          </div>
          <div className={styles.field}>
            <label htmlFor={`${uid}-otc`} className={styles.label}>TC Kimlik No</label>
            <input id={`${uid}-otc`} className={styles.input} inputMode="numeric" maxLength={11} value={otherTc} onChange={(e) => { setOtherTc(e.target.value); setDirty(true); }} />
            {tcError && <span className={styles.fieldError}>{tcError}</span>}
          </div>
          <div className={styles.field}>
            <label htmlFor={`${uid}-ophone`} className={styles.label}>Telefon</label>
            <input id={`${uid}-ophone`} type="tel" className={styles.input} value={otherPhone} onChange={(e) => { setOtherPhone(e.target.value); setDirty(true); }} />
          </div>
        </div>
      )}
      <SignaturePad onChange={(b) => { setSignature(b); if (b) setSignatureError(null); }} />
      {signatureError && <span className={styles.fieldError} role="alert">{signatureError}</span>}
    </div>
  );

  return (
    <div className={styles.page}>
      {topBar}
      <main className={styles.body}>
        <FormRenderer content={template.content} values={values} onChange={onChange} mode="fill" signatureSlot={signatureSection} />
        <div ref={errorRef}>
          {formErrors.length > 0 && (
            <ul className={styles.error} role="alert">
              {formErrors.map((m) => <li key={m}>{m}</li>)}
            </ul>
          )}
          {submitError && <div className={styles.error} role="alert">{submitError}</div>}
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.primary} disabled={busy} onClick={submit}>
            {busy ? 'Gönderiliyor…' : 'Onayla ve Gönder'}
          </button>
        </div>
      </main>
    </div>
  );
}
