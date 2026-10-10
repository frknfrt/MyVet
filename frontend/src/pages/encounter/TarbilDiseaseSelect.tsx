import { useEffect, useState } from 'react';
import { tarbilApi, TarbilDiseaseOption } from '../../api/tarbilApi';
import { FieldWrap, Select } from '../../components/ui/Field';

/** TARBİL reçete hastalık ağacından seçilebilir teşhis (spec 2026-10-09 S5.1/S5.6). */
export function TarbilDiseaseSelect({ value, onChange }: { value: string; onChange: (id: string) => void }) {
  const [options, setOptions] = useState<TarbilDiseaseOption[]>([]);

  useEffect(() => {
    tarbilApi
      .listDiseases()
      .then((all) => setOptions(all.filter((d) => d.selectable)))
      .catch(() => setOptions([]));
  }, []);

  return (
    <FieldWrap label="TARBİL teşhisi">
      <Select aria-label="TARBİL teşhisi" value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Seçiniz</option>
        {options.map((d) => (
          <option key={d.id} value={d.id}>
            {d.path}
          </option>
        ))}
      </Select>
    </FieldWrap>
  );
}
