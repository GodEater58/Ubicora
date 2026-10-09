-- Additive migration for the browser workspace. Run with migration owner.
CREATE TABLE IF NOT EXISTS web_workspaces (
 tenant_id uuid PRIMARY KEY REFERENCES tenants(id),
 revision bigint NOT NULL CHECK(revision>0),
 data jsonb NOT NULL CHECK(jsonb_typeof(data)='object'),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS web_receipts (
 tenant_id uuid NOT NULL REFERENCES tenants(id),
 mutation_id uuid NOT NULL,
 request_hash text NOT NULL CHECK(length(request_hash)=64),
 actor_subject text NOT NULL,
 revision bigint NOT NULL,
 accepted_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,mutation_id),
 FOREIGN KEY(tenant_id,actor_subject) REFERENCES tenant_members(tenant_id,subject)
);
ALTER TABLE web_workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE web_workspaces FORCE ROW LEVEL SECURITY;
ALTER TABLE web_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE web_receipts FORCE ROW LEVEL SECURITY;
CREATE POLICY web_read ON web_workspaces FOR SELECT USING (
 EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=web_workspaces.tenant_id
 AND m.subject=current_setting('app.user_subject',true) AND m.is_active)
);
CREATE POLICY web_insert ON web_workspaces FOR INSERT WITH CHECK (
 EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=web_workspaces.tenant_id
 AND m.subject=current_setting('app.user_subject',true) AND m.is_active AND m.role='MANAGER')
);
CREATE POLICY web_update ON web_workspaces FOR UPDATE USING (
 EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=web_workspaces.tenant_id
 AND m.subject=current_setting('app.user_subject',true) AND m.is_active AND m.role='MANAGER')
) WITH CHECK (
 EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=web_workspaces.tenant_id
 AND m.subject=current_setting('app.user_subject',true) AND m.is_active AND m.role='MANAGER')
);
CREATE POLICY web_receipt_read ON web_receipts FOR SELECT USING (
 EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=web_receipts.tenant_id
 AND m.subject=current_setting('app.user_subject',true) AND m.is_active)
);
CREATE POLICY web_receipt_insert ON web_receipts FOR INSERT WITH CHECK (
 actor_subject=current_setting('app.user_subject',true) AND
 EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=web_receipts.tenant_id
 AND m.subject=current_setting('app.user_subject',true) AND m.is_active AND m.role='MANAGER')
);
-- Grant the application's dedicated NOBYPASSRLS role SELECT/INSERT/UPDATE on
-- web_workspaces and SELECT/INSERT on web_receipts. Never grant DELETE/UPDATE
-- on the append-only receipt table, never use the migration owner for requests.
