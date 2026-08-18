import json
from types import SimpleNamespace

import httpx
import pytest

import main


@pytest.fixture
def portfolio():
    return {
        "cash": 10_000.0,
        "positions": {"AAPL": {"quantity": 3, "avg_price": 175.25}},
    }


@pytest.fixture
def prediction():
    return {"ticker": "AAPL", "direction": "UP", "confidence": 0.80}


def test_fetch_portfolio_returns_http_response_json(monkeypatch, portfolio):
    calls = []

    class Response:
        def raise_for_status(self):
            return None

        def json(self):
            return portfolio

    def fake_get(endpoint, timeout):
        calls.append((endpoint, timeout))
        return Response()

    monkeypatch.setattr(main.httpx, "get", fake_get)

    assert main.fetch_portfolio() == portfolio
    assert calls == [
        (main.PORTFOLIO_SERVICE_ENDPOINT, main.PORTFOLIO_SERVICE_TIMEOUT_SECONDS)
    ]


@pytest.mark.parametrize(
    "error",
    [httpx.ConnectError("connection refused"), ValueError("invalid JSON")],
)
def test_fetch_portfolio_returns_none_for_unavailable_or_invalid_response(monkeypatch, error):
    class Response:
        def raise_for_status(self):
            if isinstance(error, httpx.HTTPError):
                raise error

        def json(self):
            if isinstance(error, ValueError):
                raise error

    monkeypatch.setattr(main.httpx, "get", lambda *_, **__: Response())

    assert main.fetch_portfolio() is None


def test_low_confidence_prediction_does_not_create_order(portfolio):
    decision = main.evaluate_trading_decision(
        {"ticker": "AAPL", "direction": "UP", "confidence": 0.64}, portfolio
    )

    assert decision is None


def test_up_prediction_creates_risk_sized_buy_order(portfolio, prediction):
    decision = main.evaluate_trading_decision(prediction, portfolio, current_price=180.0)

    assert decision == {
        "ticker": "AAPL",
        "action": "BUY",
        "quantity": 2,
        "order_type": "LIMIT",
        "limit_price": 180.18,
        "reason": "LSTM UP prediction with 80.0% confidence",
    }


def test_down_prediction_sells_entire_open_position(portfolio):
    decision = main.evaluate_trading_decision(
        {"ticker": "AAPL", "direction": "DOWN", "confidence": 0.80},
        portfolio,
        current_price=180.0,
    )

    assert decision == {
        "ticker": "AAPL",
        "action": "SELL",
        "quantity": 3,
        "order_type": "LIMIT",
        "limit_price": 179.82,
        "reason": "LSTM DOWN prediction with 80.0% confidence",
    }


def test_down_prediction_without_position_does_not_create_order(portfolio):
    decision = main.evaluate_trading_decision(
        {"ticker": "MSFT", "direction": "DOWN", "confidence": 0.80}, portfolio
    )

    assert decision is None


class FakeChannel:
    def __init__(self, delivery=None):
        self.delivery = delivery
        self.declarations = []
        self.published = []
        self.acknowledgements = []
        self.rejections = []
        self.consumer = None
        self.stopped = False

    def queue_declare(self, **kwargs):
        self.declarations.append(kwargs)

    def basic_publish(self, **kwargs):
        self.published.append(kwargs)

    def basic_ack(self, **kwargs):
        self.acknowledgements.append(kwargs)

    def basic_nack(self, **kwargs):
        self.rejections.append(kwargs)

    def basic_consume(self, **kwargs):
        self.consumer = kwargs

    def start_consuming(self):
        if self.delivery is not None:
            self.consumer["on_message_callback"](
                self,
                SimpleNamespace(delivery_tag=42),
                None,
                json.dumps(self.delivery).encode(),
            )
        raise KeyboardInterrupt

    def stop_consuming(self):
        self.stopped = True


class FakeConnection:
    def __init__(self, channel):
        self._channel = channel
        self.closed = False

    def channel(self):
        return self._channel

    def close(self):
        self.closed = True


def run_worker_once(monkeypatch, channel, portfolio):
    connection = FakeConnection(channel)
    captured = {}

    monkeypatch.setattr(main, "fetch_portfolio", lambda: portfolio)
    monkeypatch.setattr(
        main.pika,
        "PlainCredentials",
        lambda username, password: (username, password),
    )
    monkeypatch.setattr(
        main.pika,
        "ConnectionParameters",
        lambda **kwargs: captured.setdefault("connection_parameters", kwargs),
    )
    monkeypatch.setattr(main.pika, "BlockingConnection", lambda _: connection)

    main.main()
    return connection, captured


def test_worker_uses_configured_vhost_and_queue_names(monkeypatch, portfolio):
    channel = FakeChannel()
    monkeypatch.setattr(main, "RABBITMQ_HOST", "rabbitmq.internal")
    monkeypatch.setattr(main, "RABBITMQ_PORT", 5673)
    monkeypatch.setattr(main, "RABBITMQ_VHOST", "/trading")
    monkeypatch.setattr(main, "RABBITMQ_PREDICTIONS_QUEUE", "model.predictions")
    monkeypatch.setattr(main, "RABBITMQ_APPROVED_ORDERS_QUEUE", "execution.approved")

    connection, captured = run_worker_once(monkeypatch, channel, portfolio)

    assert captured["connection_parameters"] == {
        "host": "rabbitmq.internal",
        "port": 5673,
        "virtual_host": "/trading",
        "credentials": (main.RABBITMQ_USER, main.RABBITMQ_PASS),
    }
    assert channel.declarations == [
        {"queue": "model.predictions", "durable": True},
        {"queue": "execution.approved", "durable": True},
    ]
    assert channel.consumer["queue"] == "model.predictions"
    assert connection.closed is True


def test_worker_publishes_and_acknowledges_approved_prediction(monkeypatch, portfolio, prediction):
    channel = FakeChannel(delivery=prediction)

    run_worker_once(monkeypatch, channel, portfolio)

    assert channel.published == [
        {
            "exchange": "",
            "routing_key": main.RABBITMQ_APPROVED_ORDERS_QUEUE,
            "body": json.dumps(
                {
                    "ticker": "AAPL",
                    "action": "BUY",
                    "quantity": 2,
                    "order_type": "LIMIT",
                    "limit_price": 180.18,
                    "reason": "LSTM UP prediction with 80.0% confidence",
                }
            ),
        }
    ]
    assert channel.acknowledgements == [{"delivery_tag": 42}]
    assert channel.rejections == []


def test_worker_requeues_message_when_portfolio_is_unavailable(monkeypatch, prediction):
    channel = FakeChannel(delivery=prediction)

    run_worker_once(monkeypatch, channel, None)

    assert channel.published == []
    assert channel.acknowledgements == []
    assert channel.rejections == [{"delivery_tag": 42, "requeue": True}]
