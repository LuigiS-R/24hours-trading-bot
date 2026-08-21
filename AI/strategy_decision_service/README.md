# Strategy & Decision Engine Service

An event-driven RabbitMQ worker for the trading system. It consumes model
predictions, applies confidence, sizing, and position rules, then publishes
approved limit orders for the execution service. It does not expose HTTP
endpoints or call other AI services.

## Configuration

All RabbitMQ connection and resource names are environment-configured:

| Variable | Default | Purpose |
| --- | --- | --- |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `RABBITMQ_USER` | `guest` | RabbitMQ username |
| `RABBITMQ_PASS` | `guest` | RabbitMQ password |
| `RABBITMQ_VHOST` | `/` | RabbitMQ virtual host |
| `RABBITMQ_PREDICTIONS_QUEUE` | `predictions` | Queue consumed for model predictions |
| `RABBITMQ_APPROVED_ORDERS_QUEUE` | `orders.approved` | Queue receiving approved orders (matches Order Execution Service) |

Portfolio retrieval is performed through Account Service's protected internal API:

| Variable | Default | Purpose |
| --- | --- | --- |
| `ACCOUNT_SERVICE_BASE_URL` | `http://localhost:8081` | Account Service base URL |
| `PORTFOLIO_SERVICE_TIMEOUT_SECONDS` | `5.0` | Portfolio HTTP request timeout |

Keycloak client-credentials authentication is required for every Account Service
request. These values identify the confidential `strategy-service` Keycloak
client; `KEYCLOAK_CLIENT_SECRET` must be injected through a secret manager or
deployment environment and must never be committed.

| Variable | Required | Purpose |
| --- | --- | --- |
| `KEYCLOAK_TOKEN_URL` | Yes | `https://<keycloak>/realms/<realm>/protocol/openid-connect/token` |
| `KEYCLOAK_CLIENT_ID` | Yes | Strategy's confidential client ID, e.g. `strategy-service` |
| `KEYCLOAK_CLIENT_SECRET` | Yes | Strategy client's secret |
| `KEYCLOAK_TIMEOUT_SECONDS` | No (`5.0`) | Token-request timeout |

## Run

```sh
pip install -r requirements.txt
ACCOUNT_SERVICE_BASE_URL=http://account-service:8081 KEYCLOAK_TOKEN_URL=http://keycloak:8080/realms/trading/protocol/openid-connect/token KEYCLOAK_CLIENT_ID=strategy-service KEYCLOAK_CLIENT_SECRET=... RABBITMQ_VHOST=/trading RABBITMQ_PREDICTIONS_QUEUE=model.predictions python main.py
```

Both queues are declared as durable by the worker.

## Message contracts

### Consumed prediction

```json
{
  "connectionId": "brokerage-connection-1",
  "ticker": "AAPL",
  "direction": "UP",
  "confidence": 0.81
}
```

Predictions below `0.65` are held. The strategy is evaluated independently for
each brokerage connection: an `UP` prediction may create a buy order, while a
`DOWN` prediction may create a sell order only if that connection has an open
position for the ticker.

### Published approved order

```json
{
  "ticker": "AAPL",
  "action": "BUY",
  "quantity": 2,
  "order_type": "LIMIT",
  "limit_price": 180.18,
  "reason": "LSTM UP prediction with 81.0% confidence"
}
```

The order is published to `RABBITMQ_APPROVED_ORDERS_QUEUE` through RabbitMQ's
default exchange.

## Portfolio data

For every prediction, the worker obtains a cached Keycloak service-account JWT
through the Client Credentials grant and sends it to
`GET {ACCOUNT_SERVICE_BASE_URL}/internal/v1/accounts/portfolio` as
`Authorization: Bearer <token>`. The response is assumed to have this shape:

```json
{
  "portfolios": [
    {
      "connectionId": "brokerage-connection-1",
      "cashBalance": 10000.0,
      "positions": [
        {
          "ticker": "AAPL",
          "quantity": 5,
          "averagePrice": 175.25
        }
      ]
    }
  ]
}
```

To make a decision it uses:

- that portfolio's `cashBalance`, to size a buy at 5% of its available cash;
- that portfolio's matching `positions[].quantity`, to determine whether and
  how many shares can be sold.

Each resulting approved order includes the source `connectionId` so the broker
can route it to the correct account. `averagePrice` is accepted but not
currently used. If portfolio retrieval fails, the prediction is negatively
acknowledged and requeued; no order is created without a current portfolio
snapshot.

## Tests

```sh
pip install -r requirements.txt -r requirements-dev.txt
pytest
```

The suite mocks RabbitMQ and the portfolio service. It covers portfolio
retrieval failures, decision rules, configurable RabbitMQ connection settings,
and message acknowledgement, requeue, and order-publication behavior.
