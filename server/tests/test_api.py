import os
import uuid
from datetime import datetime, timezone

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

import app as service


def test_missing_bearer_is_rejected():
    with TestClient(service.app) as client:
        assert client.get("/v1/health").status_code == 401
        assert client.post("/v1/sync/events", json={}).status_code == 401


def test_payload_validation_and_authorization_matrix():
    valid=service.Event(
        event_id=uuid.uuid4(), entity_uuid=uuid.uuid4(), resource="supplier",
        operation="CREATE", created_at_ms=1760000000000, data={}
    )
    with pytest.raises(HTTPException) as exc:
        service.validate_entity(valid)
    assert exc.value.status_code == 400
    valid.data={"uuid":str(valid.entity_uuid),"name":"Fornitore A"}
    service.validate_entity(valid)
    assert not service.ROLE_RIGHTS["VIEWER"]
    assert "staff_directory" not in service.READ_RIGHTS["VIEWER"]
    valid.data["uuid"]=str(uuid.uuid4())
    with pytest.raises(HTTPException):
        service.validate_entity(valid)


@pytest.mark.skipif(not os.environ.get("UBICORA_DATABASE_URL"),
                    reason="PostgreSQL test database not configured")
def test_authenticated_tenant_isolation_idempotency_and_change_feed():
    # Test users provisioned exclusively by CI fixture, never by the public service.
    manager="test-manager"
    tenant=uuid.UUID("11111111-1111-4111-8111-111111111111")
    other=uuid.UUID("22222222-2222-4222-8222-222222222222")
    override=service.app.dependency_overrides
    override[service.authorize]=lambda:service.Principal(subject=manager)
    event_uuid=str(uuid.uuid4())
    entity_uuid=str(uuid.uuid4())
    event={
        "event_id":event_uuid,"entity_uuid":entity_uuid,
        "resource":"cost_center","operation":"CREATE",
        "created_at_ms":1760000000000,
        "data":{"uuid":entity_uuid,"name":"Manutenzione","code":"MNT"}
    }
    try:
        with TestClient(service.app) as client:
            denied=client.post("/v1/sync/events",
                json={"protocol_version":1,"tenant_uuid":str(other),"events":[event]})
            assert denied.status_code==403,denied.text
            data={"protocol_version":1,"tenant_uuid":str(tenant),"events":[event]}
            result=client.post("/v1/sync/events",json=data)
            assert result.status_code==200,result.text
            assert result.json()["accepted_event_ids"]==[event_uuid]
            repeated=client.post("/v1/sync/events",json=data)
            assert repeated.status_code==200,repeated.text
            changed=dict(event)
            changed["data"]={**event["data"],"name":"Unapproved rewrite"}
            collision=client.post("/v1/sync/events",
                json={**data,"events":[changed]})
            assert collision.status_code==409,collision.text
            feed=client.get("/v1/sync/changes",params={"tenant_uuid":str(tenant)})
            assert feed.status_code==200,feed.text
            entries=feed.json()["events"]
            assert [entry["event_id"] for entry in entries].count(event_uuid)==1
            cursor=feed.json()["next_cursor"]
            empty=client.get("/v1/sync/changes",
                params={"tenant_uuid":str(tenant),"cursor":cursor})
            assert empty.status_code==200,empty.text
            assert not empty.json()["events"]
            # The viewer can read catalog but cannot create new records.
            override[service.authorize]=lambda:service.Principal(subject="test-viewer")
            assert client.get("/v1/sync/changes",params={"tenant_uuid":str(tenant)}).status_code==200
            event["event_id"]=str(uuid.uuid4())
            event["entity_uuid"]=str(uuid.uuid4())
            event["data"]["uuid"]=event["entity_uuid"]
            assert client.post("/v1/sync/events",json={"protocol_version":1,
                "tenant_uuid":str(tenant),"events":[event]}).status_code==403
    finally:
        override.clear()


@pytest.mark.skipif(not os.environ.get("UBICORA_DATABASE_URL"),
                    reason="PostgreSQL test database not configured")
def test_idempotent_batch_rolls_back_on_invalid_tenant_reference():
    tenant=uuid.UUID("11111111-1111-4111-8111-111111111111")
    service.app.dependency_overrides[service.authorize]=lambda:service.Principal(subject="test-manager")
    event_uuid=str(uuid.uuid4())
    id_=str(uuid.uuid4())
    body={"protocol_version":1,"tenant_uuid":str(tenant),"events":[{
        "event_id":event_uuid,"entity_uuid":id_,"resource":"asset","operation":"CREATE",
        "created_at_ms":1760000000000,
        "data":{"uuid":id_,"name":"Pressa","code":"PR001",
                "center_uuid":str(uuid.uuid4())}
    }]}
    try:
        with TestClient(service.app) as client:
            assert client.post("/v1/sync/events",json=body).status_code==409
            # Invalid reference did not create any accepted event.
            with service.connect() as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT 1 FROM accepted_events WHERE event_id=%s",(event_uuid,))
                    assert cur.fetchone() is None
    finally:
        service.app.dependency_overrides.clear()
