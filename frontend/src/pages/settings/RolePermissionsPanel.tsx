import styles from './RolePermissionsPanel.module.css';

interface MatrixRow {
  group: string;
  vet: boolean;
  technician: boolean;
  receptionist: boolean;
  admin: boolean;
}

/**
 * docs/api-conventions.md "Rol & Yetki Matrisi" ile birebir eşleşir.
 * Salt okunur — yetki kontrolü koda gömülü (@PreAuthorize), burada
 * sadece görüntülenir. Matris değişirse önce dokümanda güncellenir.
 */
const ROLE_PERMISSION_MATRIX: MatrixRow[] = [
  { group: 'Hastalar (okuma)', vet: true, technician: true, receptionist: true, admin: true },
  { group: 'Hastalar (yazma)', vet: true, technician: false, receptionist: true, admin: true },
  { group: 'Muayene / SOAP (yazma, onaylama)', vet: true, technician: false, receptionist: false, admin: true },
  { group: 'Randevular', vet: true, technician: true, receptionist: true, admin: true },
  { group: 'Faturalar, Ödemeler', vet: false, technician: false, receptionist: true, admin: true },
  { group: 'Stok (yazma)', vet: false, technician: true, receptionist: false, admin: true },
  { group: 'Ayarlar, Kullanıcı Yönetimi', vet: false, technician: false, receptionist: false, admin: true },
  { group: 'TARBİL (senkron tetikleme)', vet: false, technician: false, receptionist: false, admin: true },
];

function Cell({ allowed }: { allowed: boolean }) {
  return <div className={`${styles.cell} ${allowed ? styles.allowed : styles.denied}`}>{allowed ? '✓' : '✗'}</div>;
}

export function RolePermissionsPanel() {
  return (
    <div>
      <p className={styles.note}>
        Bu tablo salt okunur bir görünümdür. Yetki kontrolü kodda (controller seviyesinde) uygulanır ve buradan
        değiştirilemez — bir değişiklik gerekiyorsa geliştirme ekibinden talep edilmelidir.
      </p>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Endpoint Grubu</div>
          <div className={styles.cell}>Veteriner Hekim</div>
          <div className={styles.cell}>Teknisyen</div>
          <div className={styles.cell}>Resepsiyonist</div>
          <div className={styles.cell}>Yönetici</div>
        </div>
        {ROLE_PERMISSION_MATRIX.map((row) => (
          <div key={row.group} className={styles.row}>
            <div>{row.group}</div>
            <Cell allowed={row.vet} />
            <Cell allowed={row.technician} />
            <Cell allowed={row.receptionist} />
            <Cell allowed={row.admin} />
          </div>
        ))}
      </div>
    </div>
  );
}
