
"""
End-to-end tests for the full ContextEngine pipeline:
  register → ingest → embed → query → verify answer has sources

Each test class targets a specific layer. Tests are ordered so that
the pipeline dependencies are satisfied: auth before ingestion before query.
"""

import uuid
import pytest
import requests

from conftest import BASE_URL, auth_headers, ingest_event, register_test_org, wait_for_processing


class TestAuthentication:
    """User registration, login, refresh, and logout."""

    def test_register_creates_user_and_org(self):
        auth = register_test_org()
        assert auth["accessToken"]
        assert auth["refreshToken"]
        assert auth["organizationId"]
        assert auth["role"] == "ADMIN"

    def test_register_rejects_duplicate_email(self):
        uid = str(uuid.uuid4())[:8]
        payload = {
            "email": f"dup-{uid}@e2e-test.local",
            "password": "TestPassword123",
            "displayName": "Dup User",
            "organizationName": f"Dup Org {uid}",
        }
        requests.post(f"{BASE_URL}/api/v1/auth/register", json=payload, timeout=10).raise_for_status()
        resp = requests.post(f"{BASE_URL}/api/v1/auth/register", json=payload, timeout=10)
        assert resp.status_code == 409

    def test_login_returns_tokens(self, auth):
        uid = str(uuid.uuid4())[:8]
        email = f"login-{uid}@e2e-test.local"
        requests.post(f"{BASE_URL}/api/v1/auth/register", json={
            "email": email,
            "password": "TestPassword123",
            "displayName": "Login User",
            "organizationName": f"Login Org {uid}",
        }, timeout=10).raise_for_status()

        resp = requests.post(f"{BASE_URL}/api/v1/auth/login",
                             json={"email": email, "password": "TestPassword123"}, timeout=10)
        resp.raise_for_status()
        data = resp.json()
        assert data["accessToken"]
        assert data["tokenType"] == "Bearer"

    def test_login_rejects_wrong_password(self):
        uid = str(uuid.uuid4())[:8]
        email = f"wrongpass-{uid}@e2e-test.local"
        requests.post(f"{BASE_URL}/api/v1/auth/register", json={
            "email": email, "password": "TestPassword123",
            "displayName": "WP User", "organizationName": f"WP Org {uid}",
        }, timeout=10).raise_for_status()

        resp = requests.post(f"{BASE_URL}/api/v1/auth/login",
                             json={"email": email, "password": "WrongPassword"}, timeout=10)
        assert resp.status_code == 401

    def test_protected_endpoint_rejects_unauthenticated(self):
        resp = requests.get(f"{BASE_URL}/api/v1/users/me", timeout=10)
        assert resp.status_code == 401

    def test_me_returns_user_profile(self, auth):
        resp = requests.get(f"{BASE_URL}/api/v1/users/me",
                            headers=auth_headers(auth), timeout=10)
        resp.raise_for_status()
        data = resp.json()
        assert data["role"] == "ADMIN"
        assert data["organizationId"] == auth["organizationId"]


class TestIngestion:
    """Event ingestion and deduplication."""

    def test_ingest_returns_201(self, auth):
        resp_data = ingest_event(auth, "The team chose Qdrant as the vector database.")
        assert resp_data.get("id") or resp_data.get("eventId")

    def test_ingest_requires_auth(self):
        resp = requests.post(f"{BASE_URL}/api/v1/events/ingest",
                             json={"sourceId": "x", "sourceType": "DOCUMENT", "content": "test"},
                             timeout=10)
        assert resp.status_code == 401

    def test_ingest_rejects_missing_content(self, auth):
        resp = requests.post(f"{BASE_URL}/api/v1/events/ingest",
                             json={"sourceId": "x", "sourceType": "DOCUMENT"},
                             headers=auth_headers(auth), timeout=10)
        assert resp.status_code == 400

    def test_deduplication_skips_duplicate_source_id(self, auth):
        source_id = f"dedup-test-{uuid.uuid4()}"
        first = ingest_event(auth, "First version of the document.", source_id)
        second_resp = requests.post(
            f"{BASE_URL}/api/v1/events/ingest",
            json={"sourceId": source_id, "sourceType": "DOCUMENT",
                  "content": "First version of the document."},
            headers=auth_headers(auth), timeout=10,
        )
        # Should return 200 (duplicate detected) or 409 depending on implementation
        assert second_resp.status_code in (200, 409)


class TestQuery:
    """RAG query pipeline — requires the embedding pipeline to have processed the ingested doc."""

    @pytest.mark.timeout(30)
    def test_query_returns_answer_with_sources(self, auth, ingested_source_id):
        resp = requests.post(
            f"{BASE_URL}/api/v1/query",
            json={"question": "How does ContextEngine manage database migrations?"},
            headers=auth_headers(auth),
            timeout=25,
        )
        resp.raise_for_status()
        data = resp.json()

        assert data["answer"], "Answer must not be empty"
        assert isinstance(data["sources"], list), "Sources must be a list"
        assert len(data["sources"]) > 0, "Must have at least one source for a grounded answer"

    @pytest.mark.timeout(30)
    def test_second_query_is_cached(self, auth, ingested_source_id):
        question = "What database does ContextEngine use?"

        first = requests.post(f"{BASE_URL}/api/v1/query",
                              json={"question": question},
                              headers=auth_headers(auth), timeout=25)
        first.raise_for_status()

        second = requests.post(f"{BASE_URL}/api/v1/query",
                               json={"question": question},
                               headers=auth_headers(auth), timeout=25)
        second.raise_for_status()

        assert second.json()["cacheHit"] is True

    def test_query_rejects_unauthenticated(self):
        resp = requests.post(f"{BASE_URL}/api/v1/query",
                             json={"question": "test question"}, timeout=10)
        assert resp.status_code == 401

    def test_query_validates_empty_question(self, auth):
        resp = requests.post(f"{BASE_URL}/api/v1/query",
                             json={"question": ""},
                             headers=auth_headers(auth), timeout=10)
        assert resp.status_code == 400


class TestApiKeyManagement:
    """API key creation, listing, and revocation."""

    def test_create_api_key_returns_plain_key_once(self, auth):
        resp = requests.post(f"{BASE_URL}/api/v1/users/me/api-keys",
                             json={"name": "test-key"},
                             headers=auth_headers(auth), timeout=10)
        resp.raise_for_status()
        data = resp.json()
        assert data["plainKey"], "Plain key must be present on creation"
        assert data["plainKey"].startswith("ce_")

    def test_list_api_keys_does_not_expose_plain_key(self, auth):
        requests.post(f"{BASE_URL}/api/v1/users/me/api-keys",
                      json={"name": "list-test-key"},
                      headers=auth_headers(auth), timeout=10).raise_for_status()

        resp = requests.get(f"{BASE_URL}/api/v1/users/me/api-keys",
                            headers=auth_headers(auth), timeout=10)
        resp.raise_for_status()
        for key in resp.json():
            assert key.get("plainKey") is None, "Plain key must not appear in list endpoint"

    def test_delete_api_key(self, auth):
        create_resp = requests.post(f"{BASE_URL}/api/v1/users/me/api-keys",
                                    json={"name": "delete-me-key"},
                                    headers=auth_headers(auth), timeout=10)
        create_resp.raise_for_status()
        key_id = create_resp.json()["id"]

        del_resp = requests.delete(f"{BASE_URL}/api/v1/users/me/api-keys/{key_id}",
                                   headers=auth_headers(auth), timeout=10)
        assert del_resp.status_code == 204
