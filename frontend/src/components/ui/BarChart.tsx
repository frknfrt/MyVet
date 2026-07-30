interface BarChartItem {
  label: string;
  value: number;
}

interface BarChartProps {
  data: BarChartItem[];
  formatValue?: (v: number) => string;
}

export function BarChart({ data, formatValue }: BarChartProps) {
  const max = Math.max(...data.map((d) => d.value));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {data.map((d) => (
        <div key={d.label} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 90, fontSize: 12, color: 'var(--color-text-muted)', flexShrink: 0 }}>
            {d.label}
          </div>
          <div style={{ flex: 1, background: '#F0EEEA', borderRadius: 6, height: 22, overflow: 'hidden' }}>
            <div
              style={{
                width: `${(d.value / max) * 100}%`,
                height: '100%',
                background: 'var(--color-brand-500)',
                borderRadius: 6,
                transition: 'width 0.6s cubic-bezier(0.2,0.8,0.2,1)',
              }}
            />
          </div>
          <div style={{ width: 70, fontSize: 12, fontWeight: 700, textAlign: 'right', flexShrink: 0 }}>
            {formatValue ? formatValue(d.value) : d.value}
          </div>
        </div>
      ))}
    </div>
  );
}
