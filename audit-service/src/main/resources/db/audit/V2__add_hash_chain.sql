ALTER TABLE audit_events ADD COLUMN previous_hash VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN record_hash VARCHAR(64) NOT NULL DEFAULT '';

CREATE RULE prevent_update_audit AS ON UPDATE TO audit_events DO INSTEAD NOTHING;
CREATE RULE prevent_delete_audit AS ON DELETE TO audit_events DO INSTEAD NOTHING;
