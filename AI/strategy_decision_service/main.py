import os
import json
import pika

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", 5672))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "guest")

# Decision Parameters
CONFIDENCE_THRESHOLD = 0.65   # Only trade if model is >= 65% confident
MAX_PORTFOLIO_RISK_PCT = 0.05 # Max 5% of total capital per trade

# Mock local portfolio state (or fetch via Portfolio Service RPC)
portfolio = {
    "cash": 10000.0,
    "positions": {
        "AAPL": {"quantity": 0, "avg_price": 0.0}
    }
}

def evaluate_trading_decision(prediction, current_price=180.0):
    """
    Improved decision rules:
    - Checks confidence threshold to avoid low-certainty trades.
    - Sizes the position based on risk percentage and available cash.
    - Formulates limit order parameters.
    """
    ticker = prediction["ticker"]
    direction = prediction["direction"]
    confidence = prediction["confidence"]
    
    # 1. Low-confidence filter -> HOLD
    if confidence < CONFIDENCE_THRESHOLD:
        print(f" [Decision] {ticker}: Confidence {confidence:.2f} below threshold {CONFIDENCE_THRESHOLD}. HOLD.")
        return None

    # 2. Buy Decision
    if direction == "UP":
        allocated_capital = portfolio["cash"] * MAX_PORTFOLIO_RISK_PCT
        quantity = int(allocated_capital // current_price)
        
        if quantity <= 0:
            print(f" [Decision] Insufficient cash to buy {ticker}.")
            return None
        
        return {
            "ticker": ticker,
            "action": "BUY",
            "quantity": quantity,
            "order_type": "LIMIT",
            "limit_price": round(current_price * 1.001, 2), # Slight buffer for fills
            "reason": f"LSTM UP prediction with {confidence*100:.1f}% confidence"
        }

    # 3. Sell Decision
    elif direction == "DOWN":
        current_holding = portfolio["positions"].get(ticker, {}).get("quantity", 0)
        if current_holding <= 0:
            print(f" [Decision] {ticker}: Prediction DOWN, but no open positions to sell.")
            return None
        
        return {
            "ticker": ticker,
            "action": "SELL",
            "quantity": current_holding,
            "order_type": "LIMIT",
            "limit_price": round(current_price * 0.999, 2),
            "reason": f"LSTM DOWN prediction with {confidence*100:.1f}% confidence"
        }

def main():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT, credentials=credentials))
    channel = connection.channel()

    channel.queue_declare(queue="predictions", durable=True)
    channel.queue_declare(queue="approved.orders", durable=True)

    def on_prediction_received(ch, method, properties, body):
        prediction = json.loads(body)
        print(f" [*] Received prediction: {prediction}")
        
        decision = evaluate_trading_decision(prediction)
        
        if decision:
            # Publish order to execution service
            channel.basic_publish(
                exchange="",
                routing_key="approved.orders",
                body=json.dumps(decision)
            )
            print(f" [✓] Approved Order Published: {decision}")
        
        ch.basic_ack(delivery_tag=method.delivery_tag)

    channel.basic_consume(queue="predictions", on_message_callback=on_prediction_received)
    print(" [*] Strategy & Decision Engine listening on 'predictions'. Press CTRL+C to exit.")
    
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        channel.stop_consuming()
        connection.close()

if __name__ == "__main__":
    main()