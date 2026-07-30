interface LineChartProps {
  labels: string[];
  values: number[];
  height?: number;
}

/**
 * Bağımlılıksız, elle yazılmış SVG çizgi grafik. Recharts/Chart.js gibi
 * bir kütüphane eklemek yerine — bu veri boyutunda (aylık trend, ~6-12 nokta)
 * kendi SVG'mizi yazmak hem bundle boyutunu küçük tutar hem de marka
 * renklerimizle birebir kontrol sağlar.
 */
export function LineChart({ labels, values, height = 180 }: LineChartProps) {
  const width = 600;
  const padding = 24;
  const max = Math.max(...values);
  const min = Math.min(...values) * 0.95;
  const range = max - min || 1;

  const points = values.map((v, i) => {
    const x = padding + (i / (values.length - 1)) * (width - padding * 2);
    const y = height - padding - ((v - min) / range) * (height - padding * 2);
    return { x, y };
  });

  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
  const areaPath = `${linePath} L ${points[points.length - 1].x} ${height - padding} L ${points[0].x} ${height - padding} Z`;

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      <defs>
        <linearGradient id="lineFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--color-brand-500)" stopOpacity="0.18" />
          <stop offset="100%" stopColor="var(--color-brand-500)" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={areaPath} fill="url(#lineFill)" />
      <path d={linePath} fill="none" stroke="var(--color-brand-500)" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
      {points.map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r={3.5} fill="var(--color-brand-500)" stroke="#fff" strokeWidth={1.5} />
      ))}
      {labels.map((label, i) => (
        <text
          key={label}
          x={points[i].x}
          y={height - 4}
          fontSize={10.5}
          fill="var(--color-text-muted)"
          textAnchor="middle"
        >
          {label}
        </text>
      ))}
    </svg>
  );
}
