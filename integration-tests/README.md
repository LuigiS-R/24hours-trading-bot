# Local backend integration suite

Run from the repository root:

```sh
python3 integration-tests/run_backend_integration.py
```

The runner starts only disposable RabbitMQ and PostgreSQL containers, starts a local Java fake Alpaca on loopback ports 18080--18083, packages the current Spring services, executes bounded end-to-end checks, stores redacted evidence in `integration-tests/artifacts/`, and always removes Compose containers and volumes. It deliberately uses no real Alpaca or public endpoint.

The fake provider records paths, bodies, non-secret header names/values used by the services, and WebSocket subscription frames. It does not proxy traffic. The Account Service JWT is a fake, short-lived RS256 token issued by the fake provider's local JWKS endpoint; this exercises the existing resource-server configuration without Keycloak.
