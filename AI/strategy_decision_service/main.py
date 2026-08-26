import os
import json

import httpx
import pika


RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", 5672))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "guest")
RABBITMQ_VHOST = os.getenv("RABBITMQ_VHOST", "/")
RABBITMQ_PREDICTIONS_QUEUE = os.getenv("RABBITMQ_PREDICTIONS_QUEUE", "predictions")
RABBITMQ_APPROVED_ORDERS_QUEUE = os.getenv(
    "RABBITMQ_APPROVED_ORDERS_QUEUE", "orders.approved"
)
ACCOUNT_SERVICE_BASE_URL = os.getenv("ACCOUNT_SERVICE_BASE_URL", "http://localhost:8081").rstrip("/")
PORTFOLIO_SERVICE_ENDPOINT = f"{ACCOUNT_SERVICE_BASE_URL}/internal/v1/portfolio"
PORTFOLIO_SERVICE_TIMEOUT_SECONDS = float(
    os.getenv("PORTFOLIO_SERVICE_TIMEOUT_SECONDS", "5.0")
)

# Decision Parameters
CONFIDENCE_THRESHOLD = 0.65   # Only trade if model is >= 65% confident
MAX_PORTFOLIO_RISK_PCT = 0.05 # Max 5% of total capital per trade

def fetch_portfolio():
    """Retrieve the one configured account's current portfolio snapshot."""
    try:
        response = httpx.get(
            PORTFOLIO_SERVICE_ENDPOINT,
            timeout=PORTFOLIO_SERVICE_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        return response.json()
    except (httpx.HTTPError, ValueError) as error:
        print(f" [Portfolio] Unable to retrieve portfolio: {error}")
        return None


def evaluate_trading_decisions(prediction, portfolio_response, current_price=180.0):
    """
    Improved decision rules:
    - Checks confidence threshold to avoid low-certainty trades.
    - Applies the strategy to the installation's one configured brokerage account.
    - Returns at most one order.
    """
    ticker = prediction["ticker"]
    direction = prediction["direction"]
    confidence = prediction["confidence"]

    # 1. Low-confidence filter -> HOLD
    if confidence < CONFIDENCE_THRESHOLD:
        print(f" [Decision] {ticker}: Confidence {confidence:.2f} below threshold {CONFIDENCE_THRESHOLD}. HOLD.")
        return []

    account = portfolio_response.get("account")
    if not account:
        return []
    positions = portfolio_response.get("positions", [])
    if direction == "UP":
        quantity = int((float(account.get("cash") or 0) * MAX_PORTFOLIO_RISK_PCT) // current_price)
        return [] if quantity <= 0 else [{"ticker": ticker, "action": "BUY", "quantity": quantity, "order_type": "LIMIT", "limit_price": round(current_price * 1.001, 2), "reason": f"LSTM UP prediction with {confidence*100:.1f}% confidence"}]
    if direction == "DOWN":
        quantity = sum(float(position.get("quantity") or 0) for position in positions if position.get("symbol") == ticker)
        return [] if quantity <= 0 else [{"ticker": ticker, "action": "SELL", "quantity": int(quantity), "order_type": "LIMIT", "limit_price": round(current_price * 0.999, 2), "reason": f"LSTM DOWN prediction with {confidence*100:.1f}% confidence"}]
    return []

def main():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    connection = pika.BlockingConnection(
        pika.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            virtual_host=RABBITMQ_VHOST,
            credentials=credentials,
        )
    )
    channel = connection.channel()

    channel.queue_declare(queue=RABBITMQ_PREDICTIONS_QUEUE, durable=True)
    channel.queue_declare(queue=RABBITMQ_APPROVED_ORDERS_QUEUE, durable=True)

    def on_prediction_received(ch, method, properties, body):
        prediction = json.loads(body)
        print(f" [*] Received prediction: {prediction}")

        portfolio = fetch_portfolio()
        if portfolio is None:
            print(" [Decision] Portfolio unavailable. Deferring prediction for retry.")
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
            return

        decisions = evaluate_trading_decisions(prediction, portfolio)

        for decision in decisions:
            # Publish order to execution service
            channel.basic_publish(
                exchange="",
                routing_key=RABBITMQ_APPROVED_ORDERS_QUEUE,
                body=json.dumps(decision)
            )
            print(f" [✓] Approved Order Published: {decision}")
        
        ch.basic_ack(delivery_tag=method.delivery_tag)

    channel.basic_consume(
        queue=RABBITMQ_PREDICTIONS_QUEUE,
        on_message_callback=on_prediction_received,
    )
    print(
        " [*] Strategy & Decision Engine listening on "
        f"'{RABBITMQ_PREDICTIONS_QUEUE}' (vhost '{RABBITMQ_VHOST}'). Press CTRL+C to exit."
    )
    
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        channel.stop_consuming()
        connection.close()

if __name__ == "__main__":
    main()
