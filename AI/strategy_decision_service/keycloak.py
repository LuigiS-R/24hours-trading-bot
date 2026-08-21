"""Client-credentials authentication for Strategy's internal service calls.

The Strategy worker is a Keycloak service account, not an Alpaca OAuth client
and not a user-facing application.  It exchanges its confidential-client
credentials for short-lived Keycloak access tokens and reuses a token only
until shortly before it expires.
"""

import os
import threading
import time

import httpx


class KeycloakAuthenticationError(RuntimeError):
    """Raised when a service-account access token cannot be obtained safely."""


class KeycloakClientCredentials:
    """Retrieves and caches Keycloak client-credentials access tokens."""

    def __init__(
        self,
        token_url: str | None,
        client_id: str | None,
        client_secret: str | None,
        timeout_seconds: float = 5.0,
    ):
        self._token_url = token_url
        self._client_id = client_id
        self._client_secret = client_secret
        self._timeout_seconds = timeout_seconds
        self._access_token: str | None = None
        self._refresh_at = 0.0
        self._lock = threading.Lock()

    @classmethod
    def from_environment(cls) -> "KeycloakClientCredentials":
        return cls(
            token_url=os.getenv("KEYCLOAK_TOKEN_URL"),
            client_id=os.getenv("KEYCLOAK_CLIENT_ID"),
            client_secret=os.getenv("KEYCLOAK_CLIENT_SECRET"),
            timeout_seconds=float(os.getenv("KEYCLOAK_TIMEOUT_SECONDS", "5.0")),
        )

    def access_token(self) -> str:
        """Return a valid cached token, acquiring a replacement when necessary."""
        if self._is_cached_token_valid():
            return self._access_token  # type: ignore[return-value]

        with self._lock:
            if self._is_cached_token_valid():
                return self._access_token  # type: ignore[return-value]
            return self._request_token()

    def _is_cached_token_valid(self) -> bool:
        return self._access_token is not None and time.monotonic() < self._refresh_at

    def _request_token(self) -> str:
        self._validate_configuration()
        try:
            response = httpx.post(
                self._token_url,
                data={"grant_type": "client_credentials"},
                auth=(self._client_id, self._client_secret),
                timeout=self._timeout_seconds,
            )
            response.raise_for_status()
            payload = response.json()
            access_token = payload.get("access_token")
            expires_in = float(payload.get("expires_in", 0))
        except (httpx.HTTPError, TypeError, ValueError, AttributeError) as error:
            raise KeycloakAuthenticationError(
                "Unable to obtain a Keycloak service-account access token."
            ) from error

        if not isinstance(access_token, str) or not access_token.strip() or expires_in <= 0:
            raise KeycloakAuthenticationError(
                "Keycloak returned an invalid service-account token response."
            )

        # Renew slightly early to avoid sending a token that expires in transit.
        early_refresh_seconds = min(30.0, expires_in * 0.1)
        self._access_token = access_token
        self._refresh_at = time.monotonic() + max(0.0, expires_in - early_refresh_seconds)
        return access_token

    def _validate_configuration(self) -> None:
        missing = [
            name
            for name, value in {
                "KEYCLOAK_TOKEN_URL": self._token_url,
                "KEYCLOAK_CLIENT_ID": self._client_id,
                "KEYCLOAK_CLIENT_SECRET": self._client_secret,
            }.items()
            if not value or not value.strip()
        ]
        if missing:
            raise KeycloakAuthenticationError(
                "Strategy service is missing required Keycloak configuration: "
                + ", ".join(missing)
            )
