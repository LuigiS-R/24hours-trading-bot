# Price Prediction Service

This event-driven microservice predicts future stock price direction using historical market data and the output from the News Impact Prediction Service.

## Model

- **Model:** Multi-input LSTM
- **Inputs:**
  - Last 20 days of market features
  - News Impact prediction from the FinBERT service
- **Performance:**
  - Accuracy: **70.1%**

## Run

From the project root:

```bash
pip install -r requirements.txt
```

Set RabbitMQ configuration as environment variables, then start the consumer:

```bash
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USER=guest
export RABBITMQ_PASS=guest
export RABBITMQ_VHOST=/
python consumer.py
```

All RabbitMQ settings are environment based. The defaults are:


| Variable | Default | Purpose |
| --- | --- | --- |
| `RABBITMQ_HOST` | `localhost` | Broker host |
| `RABBITMQ_PORT` | `5672` | Broker port |
| `RABBITMQ_USER` | `guest` | Broker user |
| `RABBITMQ_PASS` | `guest` | Broker password |
| `RABBITMQ_VHOST` | `/` | Broker virtual host |
| `RABBITMQ_MARKET_TICKS_QUEUE` | `market.ticks` | Market-bar input queue |
| `RABBITMQ_NEWS_SENTIMENT_QUEUE` | `news.sentiment` | News-sentiment input queue |
| `RABBITMQ_PREDICTIONS_QUEUE` | `predictions` | Prediction destination/routing key |
| `RABBITMQ_PREDICTIONS_EXCHANGE` | empty string | Prediction exchange (the default exchange) |
| `RABBITMQ_QUEUE_DURABLE` | `true` | Whether declared queues are durable |

## Event flow

The consumer receives the following messages:

- `market.ticks`: `{ "ticker": "AAPL", "bar": { ...market features... } }`
- `news.sentiment`: `{ "ticker": "AAPL", "label": 1, "confidence": 0.813 }`

Price bars are retained per ticker in a fixed-size window of 20. Once the
window is full, every incoming bar discards the oldest bar and produces a new
prediction. A news-sentiment message also produces a new prediction when its
ticker already has a full price window. The resulting message is published to
the configured prediction destination:

```json
{
  "ticker": "AAPL",
  "direction": "UP",
  "probabilityUp": 0.9742,
  "confidence": 0.9742,
  "timestamp": 1723000000.0
}
```

Both input consumers use RabbitMQ automatic acknowledgement (`auto_ack=True`).

## Role in the System

This service is responsible for the Price Prediction module of the 24-Hour Trading Bot. It receives recent market data and the prediction output from the News Impact Service, then returns the predicted stock direction and confidence for the trading decision pipeline.
