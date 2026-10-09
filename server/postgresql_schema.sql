-- Reference PostgreSQL 16+ schema for a future Ubicora enterprise service.
-- NOT deployed by the Android application. Authenticate the user's OIDC/JWT
-- subject before setting app.user_subject in a transaction; deny unauthenticated sessions.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name text NOT NULL CHECK(length(display_name) BETWEEN 2 AND 160),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE tenant_members (
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    subject text NOT NULL,
    role text NOT NULL CHECK(role IN ('MANAGER','PURCHASING','WAREHOUSE','MAINTENANCE','VIEWER')),
    is_active boolean NOT NULL DEFAULT true,
    PRIMARY KEY(tenant_id,subject)
);
CREATE TABLE entity_versions (
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    resource text NOT NULL CHECK(resource IN ('cost_center','supplier','asset','staff_directory')),
    entity_id uuid NOT NULL,
    data jsonb NOT NULL CHECK(jsonb_typeof(data)='object'),
    version bigint NOT NULL DEFAULT 1 CHECK(version>0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(tenant_id,resource,entity_id)
);
CREATE TABLE accepted_events (
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_id uuid NOT NULL,
    resource text NOT NULL,
    entity_id uuid NOT NULL,
    operation text NOT NULL CHECK(operation IN ('CREATE','UPDATE','DELETE')),
    payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'),
    actor_subject text NOT NULL,
    accepted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(tenant_id,event_id),
    FOREIGN KEY(tenant_id,actor_subject) REFERENCES tenant_members(tenant_id,subject)
);
CREATE INDEX idx_accepted_events_tenant_time ON accepted_events(tenant_id,accepted_at,event_id);

-- Defense in depth, assuming app.user_subject is SET LOCAL by a trusted API
-- after validating a signed token (issuer, audience, expiry, tenant membership).
-- Never let user-controlled SQL set this variable.
ALTER TABLE entity_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE entity_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY entity_member_select ON entity_versions FOR SELECT USING (
    EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=entity_versions.tenant_id
        AND m.subject=current_setting('app.user_subject',true) AND m.is_active)
);
CREATE POLICY entity_manager_insert ON entity_versions FOR INSERT WITH CHECK (
    EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=entity_versions.tenant_id
        AND m.subject=current_setting('app.user_subject',true)
        AND m.is_active AND m.role IN ('MANAGER','PURCHASING'))
);
CREATE POLICY entity_manager_update ON entity_versions FOR UPDATE USING (
    EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=entity_versions.tenant_id
        AND m.subject=current_setting('app.user_subject',true)
        AND m.is_active AND m.role IN ('MANAGER','PURCHASING'))
) WITH CHECK (
    EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=entity_versions.tenant_id
        AND m.subject=current_setting('app.user_subject',true)
        AND m.is_active AND m.role IN ('MANAGER','PURCHASING'))
);
-- The API must additionally enforce per-resource privileges, immutable audit,
-- idempotency, version conflicts and anti-replay. RLS alone is insufficient.

-- Additional policy for operational asset editors, scoped to their own tenant.
CREATE POLICY entity_asset_staff_insert ON entity_versions FOR INSERT WITH CHECK (
    resource='asset' AND EXISTS(
        SELECT 1 FROM tenant_members m WHERE m.tenant_id=entity_versions.tenant_id
        AND m.subject=current_setting('app.user_subject',true)
        AND m.is_active AND m.role IN ('WAREHOUSE','MAINTENANCE'))
);
CREATE POLICY entity_asset_staff_update ON entity_versions FOR UPDATE USING (
    resource='asset' AND EXISTS(
        SELECT 1 FROM tenant_members m WHERE m.tenant_id=entity_versions.tenant_id
        AND m.subject=current_setting('app.user_subject',true)
        AND m.is_active AND m.role IN ('WAREHOUSE','MAINTENANCE'))
) WITH CHECK (
    resource='asset' AND EXISTS(
        SELECT 1 FROM tenant_members m WHERE m.tenant_id=entity_versions.tenant_id
        AND m.subject=current_setting('app.user_subject',true)
        AND m.is_active AND m.role IN ('WAREHOUSE','MAINTENANCE'))
);
CREATE POLICY entity_authorized_delete ON entity_versions FOR DELETE USING (
    EXISTS(SELECT 1 FROM tenant_members m WHERE m.tenant_id=entity_versions.tenant_id
        AND m.subject=current_setting('app.user_subject',true) AND m.is_active
        AND (m.role='MANAGER'
             OR (m.role='PURCHASING' AND resource IN ('supplier','cost_center'))
             OR (m.role IN ('WAREHOUSE','MAINTENANCE') AND resource='asset')))
);
-- Event receipts are written only by the authenticated API, never directly by clients.
CREATE INDEX IF NOT EXISTS idx_event_cursor
    ON accepted_events(tenant_id,accepted_at,event_id);
