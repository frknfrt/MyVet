import { apiClient } from './client';

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface BranchWorkingHoursEntry {
  dayOfWeek: DayOfWeek;
  closed: boolean;
  opensAt: string | null;
  closesAt: string | null;
}

export interface StaffShiftEntry {
  dayOfWeek: DayOfWeek;
  startsAt: string;
  endsAt: string;
}

export const workingHoursApi = {
  getBranchHours: (branchId: string) =>
    apiClient.get<BranchWorkingHoursEntry[]>(`/api/v1/branches/${branchId}/working-hours`),
  setBranchHours: (branchId: string, days: BranchWorkingHoursEntry[]) =>
    apiClient.put<void>(`/api/v1/branches/${branchId}/working-hours`, { days }),
  getStaffShifts: (staffUserId: string) => apiClient.get<StaffShiftEntry[]>(`/api/v1/staff-users/${staffUserId}/shifts`),
  setStaffShifts: (staffUserId: string, shifts: StaffShiftEntry[]) =>
    apiClient.put<void>(`/api/v1/staff-users/${staffUserId}/shifts`, { shifts }),
};
