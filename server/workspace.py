"""Versioned web workspaces. Whole-document CAS; authenticated managers write.

Kept separate from Android's local SQLite archive. A rejected write is never merged
or retried against a new revision automatically. Receipts make retries idempotent.
"""
from __future__ import annotations
import hashlib
import json
import math
import os
import uuid
from contextlib import contextmanager
from typing import Literal
from urllib.parse import urlsplit

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

MAX_BYTES = 8 * 1024 * 1024
class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

class Warehouse(Strict):
    id: uuid.UUID
    name: str = Field(min_length=1, max_length=160)
    location: str = Field(default="", max_length=300)
    width: float = Field(default=12, ge=2, le=150)
    depth: float = Field(default=8, ge=2, le=150)

class Item(Strict):
    id: uuid.UUID
    warehouseId: uuid.UUID
    sku: str = Field(min_length=1, max_length=100)
    name: str = Field(min_length=1, max_length=160)
    category: str = Field(default="Ricambi", max_length=100)
    unit: str = Field(default="pz", min_length=1, max_length=24)
    quantity: float = Field(ge=0, le=1e12)
    minimum: float = Field(default=0, ge=0, le=1e12)
    cost: float | None = Field(default=None, ge=0, le=1e12)
    supplier: str = Field(default="", max_length=160)
    leadDays: int | None = Field(default=None, ge=0, le=3650)
    location: str = Field(default="", max_length=160)
    note: str = Field(default="", max_length=2000)

class Movement(Strict):
    id: uuid.UUID
    itemId: uuid.UUID
    delta: float = Field(ge=-1e12, le=1e12)
    note: str = Field(min_length=1, max_length=2000)
    at: int = Field(ge=0)

class Placement(Strict):
    id: uuid.UUID
    warehouseId: uuid.UUID
    name: str = Field(min_length=1, max_length=160)
    x: float = Field(ge=0, le=150)
    y: float = Field(ge=0, le=150)
    width: float = Field(gt=0, le=150)
    depth: float = Field(gt=0, le=150)

class Order(Strict):
    id: uuid.UUID
    itemId: uuid.UUID
    quantity: float = Field(gt=0, le=1e12)
    received: float = Field(default=0, ge=0, le=1e12)
    price: float | None = Field(default=None, ge=0, le=1e12)
    supplier: str = Field(default="", max_length=160)
    status: Literal["DRAFT", "APPROVED", "ORDERED", "RECEIVED", "CANCELLED"]
    at: int = Field(ge=0)
    @model_validator(mode="after")
    def receipt(self):
        if self.received > self.quantity or (self.status == "RECEIVED" and self.received != self.quantity):
            raise ValueError("Invalid received quantity")
        return self

class Reservation(Strict):
    id: uuid.UUID
    itemId: uuid.UUID
    quantity: float = Field(gt=0, le=1e12)
    reference: str = Field(min_length=3, max_length=300)
    status: Literal["ACTIVE", "CONSUMED", "RELEASED"]
    at: int = Field(ge=0)

class Work(Strict):
    id: uuid.UUID
    warehouseId: uuid.UUID
    machine: str = Field(min_length=1, max_length=160)
    description: str = Field(min_length=1, max_length=2000)
    itemId: uuid.UUID | None = None
    quantity: float = Field(default=0, ge=0, le=1e12)
    hours: float = Field(default=0, ge=0, le=100000)
    rate: float | None = Field(default=None, ge=0, le=1e9)
    partsCost: float | None = Field(default=None, ge=0, le=1e15)
    status: Literal["OPEN", "DONE", "CANCELLED"]
    at: int = Field(ge=0)
    completedAt: int | None = Field(default=None, ge=0)

class Document(Strict):
    format: Literal["ubicora-web-1"]
    warehouses: list[Warehouse] = Field(max_length=100)
    items: list[Item] = Field(max_length=20000)
    movements: list[Movement] = Field(max_length=100000)
    placements: list[Placement] = Field(max_length=5000)
    orders: list[Order] = Field(max_length=20000)
    reservations: list[Reservation] = Field(max_length=20000)
    works: list[Work] = Field(max_length=20000)
    budget: float | None = Field(default=None, ge=0, le=1e15)
    @model_validator(mode="after")
    def references(self):
        for rows in (self.warehouses, self.items, self.movements, self.placements,
                     self.orders, self.reservations, self.works):
            if len({x.id for x in rows}) != len(rows):
                raise ValueError("Duplicate IDs")
        warehouses = {x.id for x in self.warehouses}
        items = {x.id: x for x in self.items}
        skus = set()
        for item in self.items:
            key = (item.warehouseId, item.sku.casefold())
            if item.warehouseId not in warehouses or key in skus:
                raise ValueError("Invalid warehouse or duplicate SKU")
            skus.add(key)
        for row in [*self.movements, *self.orders, *self.reservations]:
            if row.itemId not in items:
                raise ValueError("Invalid item reference")
        for row in [*self.placements, *self.works]:
            if row.warehouseId not in warehouses:
                raise ValueError("Invalid warehouse reference")
        reserved = {}
        for r in self.reservations:
            if r.status == "ACTIVE":
                reserved[r.itemId] = reserved.get(r.itemId, 0) + r.quantity
        for id_, quantity in reserved.items():
            if quantity > items[id_].quantity + 1e-7:
                raise ValueError("Reservations exceed stock")
        for work in self.works:
            if work.itemId is not None and (work.itemId not in items or
                items[work.itemId].warehouseId != work.warehouseId):
                raise ValueError("Invalid maintenance item")
        return self

def empty():
    return {"format": "ubicora-web-1", "warehouses": [], "items": [],
            "movements": [], "placements": [], "orders": [],
            "reservations": [], "works": [], "budget": None}

class Write(Strict):
    mutation_id: uuid.UUID
    expected_revision: int = Field(ge=0)
    data: Document

def browser_config():
    names = {"authorization_endpoint": "UBICORA_WEB_AUTHORIZE_URL",
             "token_endpoint": "UBICORA_WEB_TOKEN_URL", "client_id": "UBICORA_WEB_CLIENT_ID"}
    result = {key: os.getenv(env, "").strip() for key, env in names.items()}
    for key in ("authorization_endpoint", "token_endpoint"):
        url = urlsplit(result[key])
        if url.scheme != "https" or not url.hostname or url.username or url.password or url.fragment:
            return {"configured": False}
    if not result["client_id"]:
        return {"configured": False}
    return {"configured": True, **result, "scope": "openid profile"}

def router(authorize, connect, role_for):
    api = APIRouter(prefix="/v1/web")
    @contextmanager
    def transaction(tenant, subject):
        with connect() as conn:
            with conn.transaction():
                with conn.cursor() as cur:
                    role = role_for(cur, tenant, subject)
                    cur.execute("SELECT set_config('app.user_subject',%s,true)", (subject,))
                    yield cur, role

    @api.get("/config")
    def configuration():
        return browser_config()

    @api.get("/tenants")
    def tenants(principal=Depends(authorize)):
        with connect() as conn:
            with conn.cursor() as cur:
                cur.execute("""SELECT t.id,t.display_name,m.role FROM tenants t
                    JOIN tenant_members m ON m.tenant_id=t.id
                    WHERE m.subject=%s AND m.is_active ORDER BY t.display_name""", (principal.subject,))
                return {"tenants": [{**r, "id": str(r["id"])} for r in cur.fetchall()]}

    @api.get("/workspace/{tenant}")
    def read(tenant: uuid.UUID, principal=Depends(authorize)):
        with transaction(tenant, principal.subject) as (cur, role):
            cur.execute("SELECT revision,data FROM web_workspaces WHERE tenant_id=%s", (tenant,))
            row = cur.fetchone()
            return {"revision": row["revision"] if row else 0,
                    "data": row["data"] if row else empty(), "can_write": role == "MANAGER"}

    @api.put("/workspace/{tenant}")
    async def write(tenant: uuid.UUID, request: Request, principal=Depends(authorize)):
        data = bytearray()
        async for chunk in request.stream():
            data.extend(chunk)
            if len(data) > MAX_BYTES:
                raise HTTPException(413, "Workspace too large")
        try:
            command = Write.model_validate_json(bytes(data))
        except Exception:
            raise HTTPException(400, "Invalid workspace")
        payload = command.data.model_dump(mode="json")
        canonical = json.dumps({"expected_revision": command.expected_revision, "data": payload},
                               sort_keys=True, separators=(",", ":"), allow_nan=False)
        digest = hashlib.sha256(canonical.encode()).hexdigest()
        with transaction(tenant, principal.subject) as (cur, role):
            if role != "MANAGER":
                raise HTTPException(403, "Only authenticated managers may edit this workspace")
            cur.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s,0))", ("web:"+str(tenant),))
            cur.execute("""SELECT request_hash,actor_subject,revision FROM web_receipts
                           WHERE tenant_id=%s AND mutation_id=%s""", (tenant, command.mutation_id))
            prior = cur.fetchone()
            if prior:
                if prior["request_hash"] != digest or prior["actor_subject"] != principal.subject:
                    raise HTTPException(409, "Mutation ID reused with different data")
                return {"revision": prior["revision"], "mutation_id": str(command.mutation_id)}
            cur.execute("SELECT revision FROM web_workspaces WHERE tenant_id=%s FOR UPDATE", (tenant,))
            row = cur.fetchone()
            revision = row["revision"] if row else 0
            if revision != command.expected_revision:
                raise HTTPException(409, "Workspace changed on another device; reload before editing")
            next_revision = revision + 1
            cur.execute("""INSERT INTO web_workspaces(tenant_id,revision,data)
                    VALUES(%s,%s,%s::jsonb) ON CONFLICT(tenant_id) DO UPDATE
                    SET revision=EXCLUDED.revision,data=EXCLUDED.data,updated_at=now()""",
                        (tenant, next_revision, json.dumps(payload, allow_nan=False)))
            cur.execute("""INSERT INTO web_receipts(tenant_id,mutation_id,request_hash,actor_subject,revision)
                    VALUES(%s,%s,%s,%s,%s)""",
                        (tenant, command.mutation_id, digest, principal.subject, next_revision))
        return {"revision": next_revision, "mutation_id": str(command.mutation_id)}
    return api
