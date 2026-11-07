import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import styles from './SignaturePad.module.css';

interface SignaturePadProps {
  /** Her cizgi bitiminde PNG; bos ise null. */
  onChange: (png: Blob | null) => void;
  height?: number;
}

interface Pt {
  x: number;
  y: number;
}

function inkColor(): string {
  try {
    return getComputedStyle(document.documentElement).getPropertyValue('--color-ink-900').trim() || 'black';
  } catch {
    return 'black';
  }
}

export function SignaturePad({ onChange, height = 200 }: SignaturePadProps) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  // Cizgiler kapsayici olcusune gore normalize (x/genislik, y/yukseklik) -> boyut degisince yeniden cizilir.
  const strokes = useRef<Pt[][]>([]);
  const activeId = useRef<number | null | undefined>(null);
  const seq = useRef(0);
  const [empty, setEmpty] = useState(true);

  const getCtx = useCallback(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return null;
    ctx.lineWidth = 2.5;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = inkColor();
    return ctx;
  }, []);

  const cssWidth = () => wrapRef.current?.clientWidth || wrapRef.current?.offsetWidth || 0;

  const drawStroke = useCallback(
    (ctx: CanvasRenderingContext2D, stroke: Pt[], w: number) => {
      const px = (p: Pt): Pt => ({ x: p.x * w, y: p.y * height });
      const first = px(stroke[0]);
      ctx.beginPath();
      ctx.moveTo(first.x, first.y);
      ctx.lineTo(first.x + 0.01, first.y + 0.01);
      ctx.stroke();
      let from = first;
      for (let i = 1; i < stroke.length; i++) {
        const prev = px(stroke[i - 1]);
        const cur = px(stroke[i]);
        const mid = { x: (prev.x + cur.x) / 2, y: (prev.y + cur.y) / 2 };
        ctx.beginPath();
        ctx.moveTo(from.x, from.y);
        ctx.quadraticCurveTo(prev.x, prev.y, mid.x, mid.y);
        ctx.stroke();
        from = mid;
      }
    },
    [height],
  );

  // PNG uret; bayat (Temizle sonrasi gelen) geri cagrilar yok sayilir.
  const emit = useCallback(() => {
    const mine = ++seq.current;
    canvasRef.current?.toBlob((blob) => {
      if (mine !== seq.current || strokes.current.length === 0) return;
      onChangeRef.current(blob);
    }, 'image/png');
  }, []);

  const resize = useCallback(() => {
    const canvas = canvasRef.current;
    const w = cssWidth();
    if (!canvas || !w) return;
    const ratio = window.devicePixelRatio || 1;
    const bw = Math.round(w * ratio);
    const bh = Math.round(height * ratio);
    if (canvas.width === bw && canvas.height === bh) return;
    canvas.width = bw; // boyut degisince bitmap silinir -> cizgilerden yeniden ciz
    canvas.height = bh;
    canvas.style.height = `${height}px`;
    const ctx = getCtx();
    if (!ctx) return;
    ctx.scale(ratio, ratio);
    for (const s of strokes.current) if (s.length) drawStroke(ctx, s, w);
    if (strokes.current.length > 0 && activeId.current === null) emit();
  }, [height, getCtx, drawStroke, emit]);

  useEffect(() => {
    resize();
    const wrap = wrapRef.current;
    if (!wrap) return;
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(() => resize());
      ro.observe(wrap);
      return () => ro.disconnect();
    }
    window.addEventListener('resize', resize);
    return () => window.removeEventListener('resize', resize);
  }, [resize]);

  const point = (e: ReactPointerEvent<HTMLCanvasElement>): Pt => {
    const rect = e.currentTarget.getBoundingClientRect();
    const w = rect.width || cssWidth() || 1;
    return { x: (e.clientX - rect.left) / w, y: (e.clientY - rect.top) / height };
  };

  const onDown = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (activeId.current !== null) return;
    const ctx = getCtx();
    if (!ctx) return;
    e.preventDefault();
    e.currentTarget.setPointerCapture?.(e.pointerId);
    activeId.current = e.pointerId;
    const stroke = [point(e)];
    strokes.current.push(stroke);
    drawStroke(ctx, stroke, cssWidth());
  };

  const onMove = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (activeId.current === null || e.pointerId !== activeId.current) return;
    const ctx = getCtx();
    const stroke = strokes.current[strokes.current.length - 1];
    if (!ctx || !stroke) return;
    const w = cssWidth();
    stroke.push(point(e));
    const n = stroke.length;
    const px = (p: Pt): Pt => ({ x: p.x * w, y: p.y * height });
    const prev = px(stroke[n - 2]);
    const cur = px(stroke[n - 1]);
    const from = n > 2 ? { x: (px(stroke[n - 3]).x + prev.x) / 2, y: (px(stroke[n - 3]).y + prev.y) / 2 } : prev;
    ctx.beginPath();
    ctx.moveTo(from.x, from.y);
    ctx.quadraticCurveTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2);
    ctx.stroke();
  };

  const onUp = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (activeId.current === null || e.pointerId !== activeId.current) return;
    activeId.current = null;
    e.currentTarget.releasePointerCapture?.(e.pointerId);
    setEmpty(false);
    emit();
  };

  const clear = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (canvas && ctx) {
      ctx.save();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.restore();
    }
    strokes.current = [];
    activeId.current = null;
    seq.current++;
    setEmpty(true);
    onChangeRef.current(null);
  };

  return (
    <div className={styles.pad}>
      <div ref={wrapRef} className={styles.surface} style={{ height }}>
        <canvas
          ref={canvasRef}
          className={styles.canvas}
          aria-label="İmza alanı"
          onPointerDown={onDown}
          onPointerMove={onMove}
          onPointerUp={onUp}
          onPointerCancel={onUp}
        />
        {empty && <span className={styles.placeholder}>Buraya imzalayın</span>}
        <span className={styles.baseline} aria-hidden="true" />
      </div>
      <button type="button" className={styles.clear} onClick={clear}>
        Temizle
      </button>
    </div>
  );
}
