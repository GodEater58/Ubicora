import os
import uuid
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
import app as service
from workspace import Document, empty

TENANT = "11111111-1111-4111-8111-111111111111"
OTHER = "22222222-2222-4222-8222-222222222222"

def document():
    data=empty()
    wid=str(uuid.uuid4()); id_=str(uuid.uuid4())
    data["warehouses"]=[{"id":wid,"name":"Officina","location":"","width":12,"depth":8}]
    data["items"]=[{"id":id_,"warehouseId":wid,"sku":"CUS-000001","name":"Cuscinetto",
      "category":"Ricambi","quantity":10,"minimum":2,"unit":"pz","cost":8.5,
      "supplier":"","leadDays":None,"location":"Scaffale A","note":""}]
    return data

def test_workspace_document_references_and_quantities():
    d=document(); assert Document.model_validate(d).items[0].quantity==10
    d["items"][0]["warehouseId"]=str(uuid.uuid4())
    with pytest.raises(ValidationError): Document.model_validate(d)
    d=document();d["items"][0]["quantity"]=-1
    with pytest.raises(ValidationError):Document.model_validate(d)
    d=document();d["items"][0]["cost"]=float('nan')
    with pytest.raises(ValidationError):Document.model_validate(d)
    d=document();d["items"].append({**d["items"][0],"id":str(uuid.uuid4())})
    with pytest.raises(ValidationError):Document.model_validate(d)
    d=document();d["reservations"]=[{"id":str(uuid.uuid4()),"itemId":d["items"][0]["id"],
      "quantity":11,"reference":"Commessa","status":"ACTIVE","at":1}]
    with pytest.raises(ValidationError):Document.model_validate(d)

def test_workspace_auth_and_no_implicit_cloud_configuration(monkeypatch):
    for key in ['UBICORA_WEB_AUTHORIZE_URL','UBICORA_WEB_TOKEN_URL','UBICORA_WEB_CLIENT_ID']:
        monkeypatch.delenv(key,raising=False)
    with TestClient(service.app) as client:
        assert client.get('/v1/web/config').json()=={"configured":False}
        assert client.get('/v1/web/tenants').status_code==401
        assert client.get('/v1/web/workspace/'+TENANT).status_code==401
        assert client.put('/v1/web/workspace/'+TENANT,json={}).status_code==401
        assert client.get('/v1/web/config').headers['cache-control']=='no-store'

@pytest.mark.skipif(not os.getenv('UBICORA_DATABASE_URL'),reason='PostgreSQL not configured')
def test_shared_workspace_cas_receipts_roles_and_tenant_isolation():
    override=service.app.dependency_overrides
    override[service.authorize]=lambda:service.Principal(subject='test-manager')
    try:
        with TestClient(service.app) as client:
            assert client.get('/v1/web/workspace/'+OTHER).status_code==403
            spaces=client.get('/v1/web/tenants').json()['tenants']
            assert [x['id'] for x in spaces]==[TENANT]
            initial=client.get('/v1/web/workspace/'+TENANT).json()
            revision=initial['revision'];data=document();mutation=str(uuid.uuid4())
            body={'mutation_id':mutation,'expected_revision':revision,'data':data}
            first=client.put('/v1/web/workspace/'+TENANT,json=body)
            assert first.status_code==200,first.text
            assert first.json()['revision']==revision+1
            assert client.put('/v1/web/workspace/'+TENANT,json=body).json()==first.json()
            # Lost acknowledgement/retry is exactly-once, not a duplicate stock change.
            assert client.get('/v1/web/workspace/'+TENANT).json()['revision']==revision+1
            stale={**body,'mutation_id':str(uuid.uuid4())}
            assert client.put('/v1/web/workspace/'+TENANT,json=stale).status_code==409
            changed={**body,'expected_revision':revision+1}
            assert client.put('/v1/web/workspace/'+TENANT,json=changed).status_code==409
            assert client.put('/v1/web/workspace/'+OTHER,json=body).status_code==403
            override[service.authorize]=lambda:service.Principal(subject='test-viewer')
            readonly=client.get('/v1/web/workspace/'+TENANT)
            assert readonly.status_code==200 and readonly.json()['can_write'] is False
            assert client.put('/v1/web/workspace/'+TENANT,json=stale).status_code==403
            # Direct SQL RLS cannot cross tenants even if API checks were omitted.
            with service.connect() as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT set_config('app.user_subject','test-manager',true)")
                    cur.execute("SELECT tenant_id FROM web_workspaces WHERE tenant_id=%s",(OTHER,))
                    assert cur.fetchone() is None
    finally:override.clear()
