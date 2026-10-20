import { useEffect, useState } from 'react';
import { auditApi, type AuditActionOption } from '../../api/auditApi';
import { staffUsersApi } from '../../api/staffUsersApi';
import { AuditEventTable } from '../../components/audit/AuditEventTable';

export function ActivityHistoryPanel() {
  const [actions, setActions] = useState<AuditActionOption[] | undefined>(undefined);
  const [staff, setStaff] = useState<{ id: string; name: string }[] | undefined>(undefined);

  useEffect(() => {
    auditApi.actions().then(setActions).catch(() => setActions(undefined));
    staffUsersApi
      .list()
      .then((list) => setStaff(list.map((s) => ({ id: s.id, name: s.fullName }))))
      .catch(() => setStaff(undefined));
  }, []);

  return (
    <div>
      <p style={{ marginTop: 0, color: 'var(--color-text-muted)', fontSize: 13 }}>
        Klinikte yapılan hassas işlemlerin değiştirilemez kaydı.
      </p>
      <AuditEventTable fetchPage={auditApi.list} actionOptions={actions} staffOptions={staff} />
    </div>
  );
}
