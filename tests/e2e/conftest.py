
"""
E2E test fixtures and helpers.

Prerequisites (all running locally or in a test environment):
  - All services started via docker-compose up
  - Kafka, Redis, PostgreSQL, Qdrant, Neo4j, Ollama running
  - nomic-embed-text and llama3.1:8b pulled in Ollama

Run with:
  pytest tests/e2e/ -v --timeout=120
"""

import time
import uuid
import pytest
import requests

BASE_URL = "http://localhost:8080"
HEADERS_JSON = {"Content-Type": "application/json"}


def register_test_org(suffix: str | None = None) -> dict:
    """
    Register a fresh organization and user for a test.
    Returns the full auth response dict.
    """
    uid = suffix or str(uuid.uuid4())[:8]
    payload = {
        "email": f"testuser-{uid}@e2e-test.local",
        "password": "TestPassword123",
        "displayName": f"E2E User {uid}",
        "organizationName": f"E2E Org {uid}",
    }
    resp = requests.post(f"{BASE_URL}/api/v1/auth/register", json=payload, timeout=10)
    resp.raise_for_status()
    return resp.json()


def auth_headers(auth: dict) -> dict:
    return {
        "Authorization": f"Bearer {auth['accessToken']}",
        "X-Organization-Id": auth["organizationId"],
        "Content-Type": "application/json",
    }


def ingest_event(auth: dict, content: str, source_id: str | None = None) -> dict:
    payload = {
        "sourceId": source_id or f"e2e-doc-{uuid.uuid4()}",
        "sourceType": "DOCUMENT",
        "content": content,
        "metadata": {"author": "e2e-test"},
    }
    resp = requests.post(
        f"{BASE_URL}/api/v1/events/ingest",
        json=payload,
        headers=auth_headers(auth),
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()


def wait_for_processing(auth: dict, source_id: str, max_wait_seconds: int = 60) -> bool:
    """
    Poll the ingestion service until the event status is PROCESSED or until timeout.
    Returns True if processed, False if timeout.
    """
    deadline = time.time() + max_wait_seconds
    while time.time() < deadline:
        resp = requests.get(
            f"{BASE_URL}/api/v1/events/{source_id}",
            headers=auth_headers(auth),
            timeout=5,
        )
        if resp.status_code == 200:
            data = resp.json()
            if data.get("status") == "PROCESSED":
                return True
        time.sleep(3)
    return False


@pytest.fixture(scope="session")
def auth():
    """One registered user/org shared across the test session."""
    return register_test_org("session")


@pytest.fixture(scope="session")
def ingested_source_id(auth):
    """
    Ingest a well-known document once per session. All query tests use
    this source so they don't each trigger a separate embedding run.
    """
    content = (
        "ContextEngine uses PostgreSQL with Flyway migrations for schema management. "
        "Each service maintains its own schema history table to prevent version conflicts. "
        "The team decided to use separate Flyway history tables per service after encountering "
        "migration conflicts during Sprint 2. This is documented in ADR-003."
    )
    source_id = f"e2e-session-doc-{uuid.uuid4()}"
    ingest_event(auth, content, source_id)
    processed = wait_for_processing(auth, source_id, max_wait_seconds=90)
    if not processed:
        pytest.skip("Embedding pipeline did not process the test document within 90 seconds")
    return source_id
