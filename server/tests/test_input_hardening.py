"""4.21.2: inputs PostgreSQL would refuse later, or that slip past a check, are refused up front (no DB needed)."""
import json
import uuid

import pytest
from fastapi import HTTPException

import app as service
from workspace import empty, has_nul, prepare, without_costs


def event(resource="supplier", data=None, operation="CREATE"):
    entity = uuid.uuid4()
    body = {"uuid": str(entity), **(data or {})}
    return service.Event(event_id=uuid.uuid4(), entity_uuid=entity, resource=resource,
                         operation=operation, created_at_ms=1, data=body)


def status(call):
    with pytest.raises(HTTPException) as caught:
        call()
    return caught.value.status_code


def test_nul_characters_are_refused_before_storage():
    assert has_nul({"a": ["ok", {"b\x00": 1}]}) and not has_nul({"a": "\\u0000 typed by a person"})
    assert status(lambda: service.validate_entity(event(data={"name": "Ditta\x00Rossi"}))) == 400


def test_each_resource_is_checked_on_its_own_name_field():
    assert status(lambda: service.validate_entity(event(data={"name": "", "display_name": "Mario Rossi"}))) == 400
    service.validate_entity(event(data={"name": "Ditta Rossi"}))
    service.validate_entity(event("staff_directory", {"display_name": "Mario Rossi", "proposed_role": "VIEWER"}))


class NoRows:
    def execute(self, *_):
        pass

    def fetchone(self):
        return None


def test_asset_center_uuid_must_be_canonical():
    center = uuid.uuid4()
    upper = event("asset", {"name": "Tornio", "code": "TOR-1", "center_uuid": str(center).upper()})
    assert status(lambda: service.apply_event(NoRows(), uuid.uuid4(), upper, "user")) == 400


def test_viewer_copy_has_no_prices_and_the_original_is_untouched():
    data = {**empty(), "budget": 5000, "items": [{"id": "i", "cost": 3.5, "name": "Vite"}],
            "orders": [{"id": "o", "price": 9}], "works": [{"id": "w", "rate": 40, "partsCost": 12}]}
    hidden = without_costs(data)
    assert hidden["budget"] is None and hidden["items"][0] == {"id": "i", "cost": None, "name": "Vite"}
    assert hidden["orders"][0]["price"] is None and hidden["works"][0]["rate"] is None
    assert data["items"][0]["cost"] == 3.5 and data["budget"] == 5000


def test_served_page_may_only_call_this_server_and_the_token_endpoint(monkeypatch):
    from fastapi.testclient import TestClient
    monkeypatch.setenv("UBICORA_WEB_AUTHORIZE_URL", "https://login.example.test/authorize")
    monkeypatch.setenv("UBICORA_WEB_TOKEN_URL", "https://login.example.test/token")
    monkeypatch.setenv("UBICORA_WEB_CLIENT_ID", "ubicora-web")
    with TestClient(service.app) as client:
        policy = client.get("/").headers["Content-Security-Policy"]
    assert "connect-src 'self' https://login.example.test" in policy
    monkeypatch.delenv("UBICORA_WEB_TOKEN_URL")
    with TestClient(service.app) as client:
        assert client.get("/").headers["Content-Security-Policy"].endswith("connect-src 'self'")


def test_workspace_with_nul_text_is_refused():
    warehouse = str(uuid.uuid4())
    body = {"mutation_id": str(uuid.uuid4()), "expected_revision": 0,
            "data": {**empty(), "warehouses": [{"id": warehouse, "name": "Officina\x00"}]}}
    assert status(lambda: prepare(json.dumps(body).encode())) == 400
    body["data"]["warehouses"][0]["name"] = "Officina"
    command, payload, digest = prepare(json.dumps(body).encode())
    assert payload["warehouses"][0]["name"] == "Officina" and len(digest) == 64
