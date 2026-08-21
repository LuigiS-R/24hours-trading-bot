import httpx
import pytest

from keycloak import KeycloakAuthenticationError, KeycloakClientCredentials


def test_client_credentials_request_uses_basic_auth_and_caches_token(monkeypatch):
    calls = []

    class Response:
        def raise_for_status(self):
            return None

        def json(self):
            return {"access_token": "service-jwt", "expires_in": 300}

    def fake_post(url, *, data, auth, timeout):
        calls.append((url, data, auth, timeout))
        return Response()

    monkeypatch.setattr(httpx, "post", fake_post)
    client = KeycloakClientCredentials(
        "https://keycloak.example/realms/trading/protocol/openid-connect/token",
        "strategy-service",
        "secret-value",
        4.0,
    )

    assert client.access_token() == "service-jwt"
    assert client.access_token() == "service-jwt"
    assert calls == [
        (
            "https://keycloak.example/realms/trading/protocol/openid-connect/token",
            {"grant_type": "client_credentials"},
            ("strategy-service", "secret-value"),
            4.0,
        )
    ]


def test_missing_keycloak_configuration_is_rejected_without_requesting_a_token():
    client = KeycloakClientCredentials(None, "strategy-service", None)

    with pytest.raises(KeycloakAuthenticationError, match="KEYCLOAK_TOKEN_URL, KEYCLOAK_CLIENT_SECRET"):
        client.access_token()


def test_invalid_keycloak_token_response_is_rejected(monkeypatch):
    class Response:
        def raise_for_status(self):
            return None

        def json(self):
            return {"access_token": "", "expires_in": 300}

    monkeypatch.setattr(httpx, "post", lambda *_, **__: Response())
    client = KeycloakClientCredentials("https://keycloak.example/token", "strategy-service", "secret")

    with pytest.raises(KeycloakAuthenticationError, match="invalid service-account token response"):
        client.access_token()
