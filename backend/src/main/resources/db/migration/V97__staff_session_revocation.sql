CREATE TABLE staff_session_revocations (
    staff_id uuid PRIMARY KEY,
    revoked_at timestamptz NOT NULL
);

INSERT INTO staff_session_revocations SELECT id, clock_timestamp() FROM staff_users WHERE NOT active;

CREATE FUNCTION revoke_changed_staff_sessions() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO staff_session_revocations VALUES (OLD.id, clock_timestamp())
    ON CONFLICT (staff_id) DO UPDATE SET revoked_at = EXCLUDED.revoked_at;
    RETURN OLD;
END;
$$;

CREATE TRIGGER staff_credentials_changed AFTER UPDATE ON staff_users FOR EACH ROW
WHEN (OLD.active IS DISTINCT FROM NEW.active OR OLD.role IS DISTINCT FROM NEW.role OR
      OLD.password_hash IS DISTINCT FROM NEW.password_hash OR OLD.branch_id IS DISTINCT FROM NEW.branch_id)
EXECUTE FUNCTION revoke_changed_staff_sessions();

CREATE TRIGGER staff_removed AFTER DELETE ON staff_users FOR EACH ROW
EXECUTE FUNCTION revoke_changed_staff_sessions();
