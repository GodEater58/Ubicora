"""Regression tests added in 4.6.3: change-feed ordering, referential deletes, cursor parsing."""
import os
import time
import uuid

import pytest
from fastapi.testclient import TestClient

import app as service

TENANT = "11111111-1111-4111-8111-111111111111"
needs_db = pytest.mark.skipif(not os.environ.get("UBICORA_DATABASE_URL"),
                              reason="PostgreSQL test database not configured")


def _event(resource, data, operation="CREATE", entity=None, expected=None):
    entity = entity or str(uuid.uuid4())
    if operation != "DELETE":
        data = {"uuid": entity, **data}
    ev = {"event_id": str(uuid.uuid4()), "entity_uuid": entity, "resource": resource,
          "operation": operation, "created_at_ms": 1760000000000, "data": data}
    if expected is not None:
        ev["expected_version"] = expected
    return ev


def _push(client, *events):
    return client.post("/v1/sync/events", json={"protocol_version": 1, "tenant_uuid": TENANT,
                                                "events": list(events)})


def _drain(client, cursor):
    seen = []
    while True:
        params = {"tenant_uuid": TENANT, "limit": 250}
        if cursor:
            params["cursor"] = cursor
        body = client.get("/v1/sync/changes", params=params).json()
        seen += [e["event_id"] for e in body["events"]]
        if not body["events"]:
            return seen, cursor
        cursor = body["next_cursor"]


@pytest.fixture
def manager_client():
    service.app.dependency_overrides[service.authorize] = lambda: service.Principal(subject="test-manager")
    try:
        with TestClient(service.app) as client:
            yield client
    finally:
        service.app.dependency_overrides.clear()


def test_cursor_accepts_unencoded_plus_sign():
    # A client that forgets to URL-encode '+' sends a space instead of the UTC offset sign.
    assert service.parse_cursor("2026-10-11T10:00:00.123456 00:00|" + str(uuid.UUID(int=5)))[1] == uuid.UUID(int=5)
    with pytest.raises(Exception):
        service.parse_cursor("2026-10-11T10:00:00|" + str(uuid.UUID(int=5)))


@needs_db
def test_late_commit_is_never_skipped_by_change_feed(manager_client):
    """A writer whose transaction started first but commits last must still appear after the cursor."""
    _, cursor = _drain(manager_client, None)
    slow = _event("supplier", {"name": "Fornitore lento"})
    batch = service.EventBatch.model_validate({"protocol_version": 1, "tenant_uuid": TENANT, "events": [slow]})
    with service.connect() as conn:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute("SELECT now()")  # transaction timestamp fixed here
                time.sleep(0.05)
                fast = _event("supplier", {"name": "Fornitore veloce"})
                assert _push(manager_client, fast).status_code == 200
                seen, cursor = _drain(manager_client, cursor)
                assert fast["event_id"] in seen
                service.store_batch(cur, batch, "test-manager")
    later, _ = _drain(manager_client, cursor)
    assert slow["event_id"] in later, "late-committing event skipped by the change cursor"


def test_push_does_not_block_event_loop():
    import inspect
    src = inspect.getsource(service.push)
    assert "run_in_threadpool" in src and "connect()" not in src


@needs_db
def test_cost_center_with_assets_cannot_be_deleted(manager_client):
    center = _event("cost_center", {"name": "Reparto A", "code": "REP-A"})
    asset = _event("asset", {"name": "Tornio", "code": "TOR-1", "center_uuid": center["entity_uuid"]})
    assert _push(manager_client, center, asset).status_code == 200
    delete = _event("cost_center", {}, operation="DELETE", entity=center["entity_uuid"], expected=1)
    response = _push(manager_client, delete)
    assert response.status_code == 409
    delete_asset = _event("asset", {}, operation="DELETE", entity=asset["entity_uuid"], expected=1)
    assert _push(manager_client, delete_asset, delete).status_code == 200
