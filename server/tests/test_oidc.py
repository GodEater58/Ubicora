"""Real RS256 signatures and HTTP auth dependency; only JWKS transport is local."""
import time
from types import SimpleNamespace

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient

import app as service


@pytest.fixture
def auth(monkeypatch):
    service.app.dependency_overrides.clear()
    key=rsa.generate_private_key(public_exponent=65537,key_size=2048)
    monkeypatch.setenv("UBICORA_OIDC_ISSUER","https://identity.example.test")
    monkeypatch.setenv("UBICORA_OIDC_AUDIENCE","ubicora-api")
    class LocalJwks:
        def get_signing_key_from_jwt(self, token):
            return SimpleNamespace(key=key.public_key())
    monkeypatch.setattr(service,"jwks_client",lambda:LocalJwks())
    def token(changes=None,omit=None,signing_key=None):
        now=int(time.time())
        claims={"iss":"https://identity.example.test","aud":"ubicora-api",
                "sub":"verified-user","iat":now,"exp":now+300}
        claims.update(changes or {})
        if omit:claims.pop(omit)
        return jwt.encode(claims,signing_key or key,algorithm="RS256")
    return token


def test_valid_signed_token_reaches_authenticated_health(auth):
    with TestClient(service.app) as client:
        result=client.get("/v1/health",headers={"Authorization":"Bearer "+auth()})
        assert result.status_code==200
        assert result.json()["authenticated"] is True


@pytest.mark.parametrize("changes",[
    {"exp":int(time.time())-120},
    {"aud":"another-api"},
    {"iss":"https://attacker.example.test"},
    {"iat":int(time.time())+3600},
    {"sub":""},
    {"sub":42},
])
def test_invalid_claims_are_rejected(auth,changes):
    with TestClient(service.app) as client:
        assert client.get("/v1/health",headers={"Authorization":"Bearer "+auth(changes)}).status_code==401


@pytest.mark.parametrize("claim",["exp","iat","iss","aud","sub"])
def test_required_claims_cannot_be_omitted(auth,claim):
    with TestClient(service.app) as client:
        assert client.get("/v1/health",headers={"Authorization":"Bearer "+auth(omit=claim)}).status_code==401


def test_wrong_signature_and_unsigned_algorithm_are_rejected(auth):
    wrong=rsa.generate_private_key(public_exponent=65537,key_size=2048)
    tokens=[auth(signing_key=wrong),jwt.encode({"sub":"fake"},key="",algorithm="none"),"invalid.jwt"]
    with TestClient(service.app) as client:
        for token in tokens:
            assert client.get("/v1/health",headers={"Authorization":"Bearer "+token}).status_code==401


def test_missing_configuration_fails_closed(auth,monkeypatch):
    monkeypatch.delenv("UBICORA_OIDC_ISSUER")
    with TestClient(service.app) as client:
        assert client.get("/v1/health",headers={"Authorization":"Bearer "+auth()}).status_code==503


def test_jwks_outage_is_controlled_and_does_not_disclose_details(auth,monkeypatch):
    class Offline:
        def get_signing_key_from_jwt(self,token):
            raise jwt.PyJWKClientError("private internal identity endpoint")
    monkeypatch.setattr(service,"jwks_client",lambda:Offline())
    with TestClient(service.app) as client:
        result=client.get("/v1/health",headers={"Authorization":"Bearer "+auth()})
        assert result.status_code==503
        assert "private internal" not in result.text
