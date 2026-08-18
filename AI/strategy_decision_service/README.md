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
| `RABBITMQ_APPROVED_ORDERS_QUEUE` | `approved.orders` | Queue receiving approved orders |

Portfolio retrieval is separately configured:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORTFOLIO_SERVICE_ENDPOINT` | `http://127.0.0.1:8003/portfolio` | Placeholder portfolio-service `GET` endpoint |
| `PORTFOLIO_SERVICE_TIMEOUT_SECONDS` | `5.0` | Portfolio HTTP request timeout |

## Run

```sh
pip install -r requirements.txt
PORTFOLIO_SERVICE_ENDPOINT=http://portfolio-service:8080/portfolio RABBITMQ_VHOST=/trading RABBITMQ_PREDICTIONS_QUEUE=model.predictions python main.py
```

Both queues are declared as durable by the worker.

## Message contracts

### Consumed prediction

```json
{
  "ticker": "AAPL",
  "direction": "UP",
  "confidence": 0.81
}
```

Predictions below `0.65` are held. An `UP` prediction may create a buy order;
a `DOWN` prediction may create a sell order only when the local portfolio has
an open position for that ticker.

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

For every prediction, the worker sends an HTTP `GET` request to
`PORTFOLIO_SERVICE_ENDPOINT`. The response is assumed to have this shape:

```json
{
  "cash": 10000.0,
  "positions": {
    "AAPL": {
      "quantity": 5,
      "avg_price": 175.25
    }
  }
}
```

To make a decision it uses:

- available `cash`, to size a buy at 5% of cash;
- `positions[ticker].quantity`, to determine whether and how many shares can
  be sold.

`avg_price` is accepted but not currently used. If portfolio retrieval fails,
the prediction is negatively acknowledged and requeued; no order is created
without a current portfolio snapshot.

## Tests

```sh
pip install -r requirements.txt -r requirements-dev.txt
pytest
```

The suite mocks RabbitMQ and the portfolio service. It covers portfolio
retrieval failures, decision rules, configurable RabbitMQ connection settings,
and message acknowledgement, requeue, and order-publication behavior.
