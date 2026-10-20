import { apiClient } from './client';

export interface AuditEvent {
  id: string;
  occurredAt: string;
  actorType: 'STAFF' | 'PLATFORM_ADMIN';
  actorId: string | null;
  actorName: string | null;
  actorRole: string | null;
  impersonatedBy: string | null;
  channel: 'WEB' | 'EXTENSION';
  action: string;
  actionLabel: string;
  targetType: string | null;
  targetId: string | null;
  targetLabel: string | null;
  summary: string | null;
  ipAddress: string | null;
}

export interface AuditEventPage {
  items: AuditEvent[];
  page: number;
  size: number;
  hasMore: boolean;
}

export interface AuditActionOption {
  code: string;
  label: string;
}

export interface AuditQuery {
  from?: string; // ISO instant
  to?: string; // ISO instant (haric)
  action?: string;
  actorId?: string;
  page: number;
  size?: number;
}

export function toQueryString(q: AuditQuery): string {
  const params = new URLSearchParams();
  if (q.from) params.set('from', q.from);
  if (q.to) params.set('to', q.to);
  if (q.action) params.set('action', q.action);
  if (q.actorId) params.set('actorId', q.actorId);
  params.set('page', String(q.page));
  params.set('size', String(q.size ?? 50));
  return params.toString();
}

export const auditApi = {
  list: (q: AuditQuery) => apiClient.get<AuditEventPage>(`/api/v1/audit-events?${toQueryString(q)}`),
  actions: () => apiClient.get<AuditActionOption[]>('/api/v1/audit-events/actions'),
};
