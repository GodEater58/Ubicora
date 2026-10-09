"""Ubicora Enterprise server. Deploy ONLY behind HTTPS with verified OIDC and PostgreSQL.

No self-registration or local administrative shortcut: memberships are provisioned by an
organization administrator outside this API. A client-side role label is never trusted.
"""
from __future__ import annotations

import json
import os
import re
import uuid
from datetime import datetime
from functools import lru_cache
from typing import Any, Literal

import jwt
import psycopg
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from jwt import PyJWKClient, InvalidTokenError, PyJWKClientError
from pydantic import BaseModel, Field, ConfigDict
from psycopg.rows import dict_row

MAX_BATCH_BYTES = 512 * 1024
RESOURCES = ("cost_center", "supplier", "asset", "staff_directory")
ROLE_RIGHTS = {
    "MANAGER": set(RESOURCES),
    "PURCHASING": {"cost_center", "supplier"},
    "MAINTENANCE": {"asset"},
    "WAREHOUSE": {"asset"},
    "VIEWER": set(),
}
READ_RIGHTS = {
    "MANAGER": set(RESOURCES),
    "PURCHASING": {"cost_center", "supplier", "asset"},
    "MAINTENANCE": {"cost_center", "supplier", "asset"},
    "WAREHOUSE": {"cost_center", "supplier", "asset"},
    "VIEWER": {"cost_center", "supplier", "asset"},
}

app = FastAPI(title="Ubicora Enterprise", version="1.0.0")


class Event(BaseModel):
    model_config = ConfigDict(extra="forbid")
    event_id: uuid.UUID
    entity_uuid: uuid.UUID
    resource: Literal["cost_center", "supplier", "asset", "staff_directory"]
    operation: Literal["CREATE", "UPDATE", "DELETE"]
    created_at_ms: int = Field(ge=0)
    data: dict[str, Any]
    expected_version: int | None = Field(default=None, ge=1)


class EventBatch(BaseModel):
    model_config = ConfigDict(extra="forbid")
    protocol_version: Literal[1]
    tenant_uuid: uuid.UUID
    events: list[Event] = Field(min_length=1, max_length=100)


class Principal(BaseModel):
    subject: str


@lru_cache(maxsize=1)
def jwks_client() -> PyJWKClient:
    url = os.environ.get("UBICORA_OIDC_JWKS_URL", "")
    if not url.startswith("https://") or "@" in url:
        raise RuntimeError("Set UBICORA_OIDC_JWKS_URL to a trusted HTTPS JWKS endpoint")
    return PyJWKClient(url, cache_keys=True, lifespan=3600)


def config(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required server configuration {name}")
    return value


def authorize(authorization: str | None = Header(default=None)) -> Principal:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Bearer token required")
    token = authorization[7:]
    if len(token) > 16384:
        raise HTTPException(401, "Invalid bearer token")
    try:
        issuer = config("UBICORA_OIDC_ISSUER")
        audience = config("UBICORA_OIDC_AUDIENCE")
        key = jwks_client().get_signing_key_from_jwt(token)
        claims = jwt.decode(
            token, key.key, algorithms=["RS256"], issuer=issuer, audience=audience,
            options={"require": ["exp", "iat", "iss", "aud", "sub"]},
            leeway=30,
        )
        subject = claims.get("sub", "")
        if not isinstance(subject, str) or not 1 <= len(subject) <= 256:
            raise InvalidTokenError("Missing subject")
        return Principal(subject=subject)
    except (InvalidTokenError, ValueError):
        raise HTTPException(401, "Invalid or expired bearer token")
    except PyJWKClientError:
        raise HTTPException(503, "Authentication service temporarily unavailable")
    except RuntimeError:
        # Misconfigured servers must fail closed.
        raise HTTPException(503, "Authentication service not configured")


def connect():
    return psycopg.connect(config("UBICORA_DATABASE_URL"), row_factory=dict_row,
                           connect_timeout=8, autocommit=False)


def role_for(cur, tenant: uuid.UUID, subject: str) -> str:
    cur.execute("""SELECT role FROM tenant_members
                   WHERE tenant_id=%s AND subject=%s AND is_active=true""",
                (tenant, subject))
    row = cur.fetchone()
    if row is None:
        raise HTTPException(403, "No active membership for this tenant")
    return row["role"]


def validate_entity(event: Event) -> None:
    if event.operation == "DELETE":
        if event.data:
            raise HTTPException(400, "DELETE payload must be empty")
        return
    if str(event.entity_uuid) != event.data.get("uuid"):
        raise HTTPException(400, "Payload entity UUID does not match event")
    try:
        size = len(json.dumps(event.data, separators=(",", ":"), allow_nan=False))
    except (ValueError, TypeError):
        raise HTTPException(400, "Invalid entity JSON")
    if size > 48_000:
        raise HTTPException(413, "Entity payload too large")
    name = event.data.get("name") or event.data.get("display_name")
    if not isinstance(name, str) or not 2 <= len(name.strip()) <= 160:
        raise HTTPException(400, "Entity name is invalid")
    if event.resource in {"cost_center", "asset"}:
        code = event.data.get("code")
        if not isinstance(code, str) or not re.fullmatch(r"[A-Za-z0-9_-]{2,32}", code):
            raise HTTPException(400, "Entity code is invalid")
    if event.resource == "staff_directory":
        if event.data.get("proposed_role") not in ROLE_RIGHTS:
            raise HTTPException(400, "Unknown directory role")


def apply_event(cur, tenant: uuid.UUID, event: Event, subject: str) -> None:
    validate_entity(event)
    cur.execute("""SELECT resource,entity_id,operation,payload,actor_subject
                   FROM accepted_events WHERE tenant_id=%s AND event_id=%s""",
                (tenant, event.event_id))
    prior = cur.fetchone()
    if prior:
        if (prior["resource"] != event.resource or
            str(prior["entity_id"]) != str(event.entity_uuid) or
            prior["operation"] != event.operation or
            prior["payload"] != event.data or prior["actor_subject"] != subject):
            raise HTTPException(409, "Event ID reused with different payload")
        return

    if event.operation != "DELETE" and event.resource == "asset":
        parent = event.data.get("center_uuid")
        if parent is not None:
            try:
                center = uuid.UUID(str(parent))
            except ValueError:
                raise HTTPException(400, "Invalid asset center UUID")
            cur.execute("""SELECT 1 FROM entity_versions
                           WHERE tenant_id=%s AND resource='cost_center' AND entity_id=%s""",
                        (tenant, center))
            if cur.fetchone() is None:
                raise HTTPException(409, "Asset center does not belong to tenant")
    cur.execute("""SELECT version FROM entity_versions
                   WHERE tenant_id=%s AND resource=%s AND entity_id=%s FOR UPDATE""",
                (tenant, event.resource, event.entity_uuid))
    existing = cur.fetchone()
    if event.operation == "CREATE":
        if existing:
            raise HTTPException(409, "Entity already exists")
        if event.expected_version is not None:
            raise HTTPException(400, "CREATE must not specify expected_version")
        cur.execute("""INSERT INTO entity_versions(tenant_id,resource,entity_id,data)
                       VALUES(%s,%s,%s,%s::jsonb)""",
                    (tenant, event.resource, event.entity_uuid, json.dumps(event.data)))
    else:
        if not existing:
            raise HTTPException(409, "Entity not found")
        if event.expected_version is None or existing["version"] != event.expected_version:
            raise HTTPException(409, "Entity version conflict")
        if event.operation == "UPDATE":
            cur.execute("""UPDATE entity_versions SET data=%s::jsonb, version=version+1, updated_at=now()
                           WHERE tenant_id=%s AND resource=%s AND entity_id=%s""",
                        (json.dumps(event.data), tenant, event.resource, event.entity_uuid))
        else:
            cur.execute("""DELETE FROM entity_versions
                           WHERE tenant_id=%s AND resource=%s AND entity_id=%s""",
                        (tenant, event.resource, event.entity_uuid))
    cur.execute("""INSERT INTO accepted_events(
                    tenant_id,event_id,resource,entity_id,operation,payload,actor_subject)
                   VALUES(%s,%s,%s,%s,%s,%s::jsonb,%s)""",
                (tenant, event.event_id, event.resource, event.entity_uuid,
                 event.operation, json.dumps(event.data), subject))


@app.exception_handler(psycopg.Error)
async def database_failure(_request: Request, _exc: psycopg.Error):
    # Never disclose connection strings or SQL details to clients.
    return JSONResponse(status_code=503, content={"detail": "Storage temporarily unavailable"})


@app.get("/v1/health")
def health(principal: Principal = Depends(authorize)):
    return {"protocol_version": 1, "authenticated": bool(principal.subject)}


@app.post("/v1/sync/events")
async def push(request: Request, principal: Principal = Depends(authorize)):
    content_length = request.headers.get("content-length")
    if content_length and (not content_length.isdigit() or int(content_length) > MAX_BATCH_BYTES):
        raise HTTPException(413, "Request too large")
    data = bytearray()
    async for chunk in request.stream():
        data.extend(chunk)
        if len(data) > MAX_BATCH_BYTES:
            raise HTTPException(413, "Request too large")
    try:
        batch = EventBatch.model_validate_json(bytes(data))
    except Exception:
        raise HTTPException(400, "Invalid sync payload")
    event_ids = [e.event_id for e in batch.events]
    if len(event_ids) != len(set(event_ids)):
        raise HTTPException(400, "Duplicate event IDs within batch")
    with connect() as connection:
        with connection.transaction():
            with connection.cursor() as cur:
                role = role_for(cur, batch.tenant_uuid, principal.subject)
                # Serialize writers per tenant until commit so change cursors cannot skip late commits.
                cur.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
                            (str(batch.tenant_uuid),))
                cur.execute("SELECT set_config('app.user_subject', %s, true)", (principal.subject,))
                for event in batch.events:
                    if event.resource not in ROLE_RIGHTS.get(role, set()):
                        raise HTTPException(403, "Role is not authorized for resource")
                    apply_event(cur, batch.tenant_uuid, event, principal.subject)
    return {"protocol_version": 1, "accepted_event_ids": [str(e) for e in event_ids]}


@app.get("/v1/sync/changes")
def changes(tenant_uuid: uuid.UUID, cursor: str | None = None,
            limit: int = Query(default=100, ge=1, le=250),
            principal: Principal = Depends(authorize)):
    start_time = datetime.min.replace(tzinfo=__import__("datetime").timezone.utc)
    start_id = uuid.UUID(int=0)
    if cursor:
        try:
            time_part, uuid_part = cursor.split("|", 1)
            start_time = datetime.fromisoformat(time_part)
            if start_time.tzinfo is None:
                raise ValueError("Timezone missing")
            start_id = uuid.UUID(uuid_part)
        except (ValueError, TypeError):
            raise HTTPException(400, "Invalid sync cursor")
    with connect() as connection:
        with connection.transaction():
            with connection.cursor() as cur:
                role = role_for(cur, tenant_uuid, principal.subject)
                allowed = sorted(READ_RIGHTS.get(role, set()))
                cur.execute("""SELECT event_id,resource,entity_id,operation,payload,accepted_at
                               FROM accepted_events
                               WHERE tenant_id=%s AND resource=ANY(%s)
                                 AND (accepted_at,event_id)>(%s,%s)
                               ORDER BY accepted_at,event_id LIMIT %s""",
                            (tenant_uuid, allowed, start_time, start_id, limit))
                rows = cur.fetchall()
    events = [{
        "event_id": str(x["event_id"]), "entity_uuid": str(x["entity_id"]),
        "resource": x["resource"], "operation": x["operation"],
        "data": x["payload"], "accepted_at": x["accepted_at"].isoformat()
    } for x in rows]
    next_cursor = (rows[-1]["accepted_at"].isoformat() + "|" + str(rows[-1]["event_id"])) if rows else cursor
    return {"protocol_version": 1, "events": events, "next_cursor": next_cursor}

