import uuid
from fastapi.testclient import TestClient
import pytest
from app.core.settings import settings
from app.main import app

client = TestClient(app)


def test_health_endpoint_is_public():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_internal_endpoint_rejects_missing_api_key():
    response = client.post(
        "/internal/v1/retrieve",
        json={"query": "test query", "owner_id": str(uuid.uuid4())},
    )
    assert response.status_code == 401


def test_internal_endpoint_rejects_invalid_api_key():
    response = client.post(
        "/internal/v1/retrieve",
        json={"query": "test query", "owner_id": str(uuid.uuid4())},
        headers={"X-Internal-Api-Key": "invalid-key"},
    )
    assert response.status_code == 401


def test_internal_endpoint_accepts_valid_api_key():
    response = client.post(
        "/internal/v1/retrieve",
        json={"query": "test query", "owner_id": str(uuid.uuid4())},
        headers={"X-Internal-Api-Key": settings.internal_api_key},
    )
    assert response.status_code == 200
    assert "chunks" in response.json()


def test_delete_vectors_rejects_missing_key():
    response = client.request(
        "DELETE",
        "/internal/v1/vectors",
        json={"vectorPointIds": [str(uuid.uuid4())]},
    )
    assert response.status_code == 401


def test_delete_vectors_accepts_valid_key():
    response = client.request(
        "DELETE",
        "/internal/v1/vectors",
        json={"vectorPointIds": [str(uuid.uuid4())]},
        headers={"X-Internal-Api-Key": settings.internal_api_key},
    )
    assert response.status_code == 204


def test_ingest_endpoint_rejects_missing_or_invalid_key():
    res1 = client.post("/internal/v1/ingestions", data={"researchItemId": str(uuid.uuid4())})
    assert res1.status_code == 401

    res2 = client.post(
        "/internal/v1/ingestions",
        data={"researchItemId": str(uuid.uuid4())},
        headers={"X-Internal-Api-Key": "wrong-key"},
    )
    assert res2.status_code == 401


def test_synthesize_endpoint_rejects_missing_or_invalid_key():
    res1 = client.post("/internal/v1/synthesize", json={"query": "test"})
    assert res1.status_code == 401

    res2 = client.post(
        "/internal/v1/synthesize",
        json={"query": "test"},
        headers={"X-Internal-Api-Key": "wrong-key"},
    )
    assert res2.status_code == 401

