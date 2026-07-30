import { BadgeTone } from '../components/ui/Badge';

export type PatientStatus = 'active' | 'review' | 'lab' | 'boarding' | 'refill' | 'appt' | 'imaging';

export interface WorklistPatient {
  id: string;
  name: string;
  species: string;
  owner: string;
  initials: string;
  color: string;
  time: string;
  location: string;
  vetInitials: string;
  assignedInitials: string[];
  reason: string;
  status: PatientStatus;
  badgeLabel: string;
  badgeTone: BadgeTone;
  directive?: 'cpr' | 'dnr';
  flag?: string;
  mine: boolean;
}

export const STATUS_TABS: { key: PatientStatus; label: string }[] = [
  { key: 'active', label: 'Aktif hastalar' },
  { key: 'review', label: 'İncelemede' },
  { key: 'lab', label: 'Lab bekleyen' },
  { key: 'boarding', label: 'Yatan hasta' },
  { key: 'refill', label: 'Reçete yenileme' },
  { key: 'appt', label: 'Bugünün randevuları' },
  { key: 'imaging', label: 'Görüntüleme' },
];

// NOT: Bu mock veri, backend API'ye bağlandığında bir react-query/fetch
// hook'u ile değiştirilecek (örn. useWorklistPatients()). Bileşen ağacı
// (WorklistTable, Tabs) değişmeden kalır — sadece veri kaynağı değişir.
export const MOCK_PATIENTS: WorklistPatient[] = [
  {
    id: '1',
    name: 'Zeytin',
    species: 'Kedi',
    owner: 'Merve Kaya',
    initials: 'ZE',
    color: '#7C4DBC',
    time: '09:15',
    location: 'Muayene 2',
    vetInitials: 'EÖ',
    assignedInitials: ['AY'],
    reason: 'Genel muayene ve aşı',
    status: 'active',
    badgeLabel: 'Aktif',
    badgeTone: 'success',
    mine: true,
  },
  {
    id: '2',
    name: 'Rex',
    species: 'Köpek',
    owner: 'Ahmet Yıldız',
    initials: 'RX',
    color: '#C2872E',
    time: '08:42',
    location: 'Muayene 1',
    vetInitials: 'EÖ',
    assignedInitials: [],
    reason: 'Sağ arka bacakta topallama',
    status: 'review',
    badgeLabel: 'AI taslak hazır',
    badgeTone: 'ai',
    directive: 'cpr',
    flag: 'Agresif olabilir',
    mine: true,
  },
  {
    id: '3',
    name: 'Momo',
    species: 'Tavşan',
    owner: 'Selin Toprak',
    initials: 'MO',
    color: '#A65022',
    time: '26.07, 16:20',
    location: 'Yatan hasta',
    vetInitials: 'CB',
    assignedInitials: ['HT', 'AY'],
    reason: 'İştahsızlık, gözlem altında',
    status: 'boarding',
    badgeLabel: 'Takip gerekli',
    badgeTone: 'warning',
    mine: false,
  },
  {
    id: '4',
    name: 'Buse',
    species: 'Kedi',
    owner: 'Jordan Porter',
    initials: 'BU',
    color: '#5046E5',
    time: '25.07, 11:51',
    location: 'Lab bekleniyor',
    vetInitials: 'CB',
    assignedInitials: [],
    reason: 'Kusma, kan tahlili istendi',
    status: 'lab',
    badgeLabel: 'Lab bekliyor',
    badgeTone: 'neutral',
    flag: 'Ödeme gecikmesi',
    mine: false,
  },
  {
    id: '5',
    name: 'Duman',
    species: 'Köpek',
    owner: 'Ece Aydın',
    initials: 'DU',
    color: '#146245',
    time: '27.07, 10:05',
    location: 'Muayene 3',
    vetInitials: 'CB',
    assignedInitials: ['HT'],
    reason: 'Kontrol muayenesi',
    status: 'refill',
    badgeLabel: 'Reçete yenileme',
    badgeTone: 'danger',
    directive: 'dnr',
    mine: false,
  },
];
