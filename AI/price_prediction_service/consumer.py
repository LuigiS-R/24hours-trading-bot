import os
import json
import time
from collections import defaultdict, deque

import pika
import numpy as np
import tensorflow as tf
import joblib
from huggingface_hub import hf_hub_download

# 1. Environment & Configuration
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", 5672))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "guest")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "guest")
RABBITMQ_VHOST = os.getenv("RABBITMQ_VHOST", "/")
# Defaults match the resource names declared by the existing Java publishers.
RABBITMQ_MARKET_TICKS_QUEUE = os.getenv("RABBITMQ_MARKET_TICKS_QUEUE", "market-data")
RABBITMQ_NEWS_SENTIMENT_QUEUE = os.getenv("RABBITMQ_NEWS_SENTIMENT_QUEUE", "news-sentiment")
RABBITMQ_PREDICTIONS_QUEUE = os.getenv("RABBITMQ_PREDICTIONS_QUEUE", "predictions")
RABBITMQ_PREDICTIONS_EXCHANGE = os.getenv("RABBITMQ_PREDICTIONS_EXCHANGE", "")
RABBITMQ_QUEUE_DURABLE = os.getenv("RABBITMQ_QUEUE_DURABLE", "true").lower() == "true"
RABBITMQ_MARKET_DATA_EXCHANGE = os.getenv(
    "RABBITMQ_MARKET_DATA_EXCHANGE", "market-data-exchange"
)
RABBITMQ_MARKET_DATA_ROUTING_KEY = os.getenv(
    "RABBITMQ_MARKET_DATA_ROUTING_KEY", "market-data"
)
RABBITMQ_NEWS_SENTIMENT_EXCHANGE = os.getenv(
    "RABBITMQ_NEWS_SENTIMENT_EXCHANGE", "news-sentiment-exchange"
)
RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY = os.getenv(
    "RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY", "news-sentiment"
)

REPO_ID = "andreamena/price-prediction-lstm-stock-news"
MODEL_FILE = "price_prediction_lstm_v1.keras"
PRICE_SCALER_FILE = "price_scaler_v1.pkl"
NEWS_SCALER_FILE = "news_scaler_v1.pkl"
METADATA_FILE = "metadata.json"

# 2. Load Model & Scalers
print("Loading model artifacts...")
model_path = hf_hub_download(repo_id=REPO_ID, filename=MODEL_FILE)
price_scaler_path = hf_hub_download(repo_id=REPO_ID, filename=PRICE_SCALER_FILE)
news_scaler_path = hf_hub_download(repo_id=REPO_ID, filename=NEWS_SCALER_FILE)
metadata_path = hf_hub_download(repo_id=REPO_ID, filename=METADATA_FILE)

model = tf.keras.models.load_model(model_path)
price_scaler = joblib.load(price_scaler_path)
news_scaler = joblib.load(news_scaler_path)
with open(metadata_path, "r") as f:
    metadata = json.load(f)

# In-memory storage for rolling price sequences and latest news per ticker.
# Each deque discards its oldest item as soon as a new bar exceeds the window.
SEQUENCE_LENGTH = metadata.get("sequence_length", 20)
price_buffer = defaultdict(lambda: deque(maxlen=SEQUENCE_LENGTH))
latest_news = {}   # { "AAPL": {"sentiment": 1, "confidence": 0.85, "timestamp": 1723000000} }

def apply_sentiment_decay(sentiment_data):
    """
    Decays news sentiment towards 0 over time (e.g., half-life of 30 minutes).
    If no news exists, returns a neutral 0.0 impact.
    """
    if not sentiment_data:
        return 0.0, 0.0, 0.0
    
    elapsed_minutes = (time.time() - sentiment_data["timestamp"]) / 60.0
    decay_factor = np.exp(-0.023 * elapsed_minutes) # decays significantly over 30-60 mins
    
    news_sentiment = sentiment_data["sentiment"]
    impact_confidence = sentiment_data["confidence"] * decay_factor
    weighted_news_impact = news_sentiment * impact_confidence
    
    return news_sentiment, impact_confidence, weighted_news_impact

def process_prediction(ticker, channel):
    """Runs LSTM inference when a complete price window is buffered."""
    bars = price_buffer.get(ticker)
    if bars is None or len(bars) < SEQUENCE_LENGTH:
        return

    price_features = metadata["price_features"]
    price_array = np.array(
        [[bar[feature] for feature in price_features] for bar in bars],
        dtype=np.float32,
    )
    
    # Apply time-decay to news
    s_val, s_conf, s_weighted = apply_sentiment_decay(latest_news.get(ticker))
    news_array = np.array([[s_val, s_conf, s_weighted]], dtype=np.float32)

    # Scale inputs
    price_scaled = price_scaler.transform(price_array).reshape(
        1, SEQUENCE_LENGTH, len(price_features)
    )
    news_scaled = news_scaler.transform(news_array)

    # Predict
    prob = float(model.predict([price_scaled, news_scaled], verbose=0)[0][0])
    direction = "UP" if prob >= 0.5 else "DOWN"
    confidence = prob if prob >= 0.5 else 1 - prob

    prediction_payload = {
        "ticker": ticker,
        "direction": direction,
        "probabilityUp": round(prob, 4),
        "confidence": round(confidence, 4),
        "timestamp": time.time()
    }

    # Publish to the configured prediction destination.
    channel.basic_publish(
        exchange=RABBITMQ_PREDICTIONS_EXCHANGE,
        routing_key=RABBITMQ_PREDICTIONS_QUEUE,
        body=json.dumps(prediction_payload)
    )
    print(f" [x] Published prediction for {ticker}: {direction} ({confidence*100:.1f}%)")

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

    # Bind the input queues to the direct exchanges used by the Java Market Data
    # Service and the News Impact Service. Queue declaration alone does not bind
    # a queue to a RabbitMQ direct exchange.
    channel.exchange_declare(
        exchange=RABBITMQ_MARKET_DATA_EXCHANGE,
        exchange_type="direct",
        durable=RABBITMQ_QUEUE_DURABLE,
    )
    channel.exchange_declare(
        exchange=RABBITMQ_NEWS_SENTIMENT_EXCHANGE,
        exchange_type="direct",
        durable=RABBITMQ_QUEUE_DURABLE,
    )
    channel.queue_declare(queue=RABBITMQ_MARKET_TICKS_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    channel.queue_declare(queue=RABBITMQ_NEWS_SENTIMENT_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    channel.queue_declare(queue=RABBITMQ_PREDICTIONS_QUEUE, durable=RABBITMQ_QUEUE_DURABLE)
    channel.queue_bind(
        exchange=RABBITMQ_MARKET_DATA_EXCHANGE,
        queue=RABBITMQ_MARKET_TICKS_QUEUE,
        routing_key=RABBITMQ_MARKET_DATA_ROUTING_KEY,
    )
    channel.queue_bind(
        exchange=RABBITMQ_NEWS_SENTIMENT_EXCHANGE,
        queue=RABBITMQ_NEWS_SENTIMENT_QUEUE,
        routing_key=RABBITMQ_NEWS_SENTIMENT_ROUTING_KEY,
    )

    def on_market_tick(ch, method, properties, body):
        tick = json.loads(body)
        ticker = tick["ticker"]
        price_buffer[ticker].append(tick["bar"])
        process_prediction(ticker, ch)

    def on_news_sentiment(ch, method, properties, body):
        news = json.loads(body)
        ticker = news["ticker"]
        latest_news[ticker] = {
            "sentiment": 1 if news.get("label") == 1 else -1,
            "confidence": news.get("confidence", 0.5),
            "timestamp": time.time()
        }
        print(f" [*] Updated sentiment for {ticker}")
        process_prediction(ticker, ch)

    channel.basic_consume(
        queue=RABBITMQ_MARKET_TICKS_QUEUE,
        on_message_callback=on_market_tick,
        auto_ack=True,
    )
    channel.basic_consume(
        queue=RABBITMQ_NEWS_SENTIMENT_QUEUE,
        on_message_callback=on_news_sentiment,
        auto_ack=True,
    )

    print(
        " [*] Prediction Service listening on "
        f"'{RABBITMQ_MARKET_TICKS_QUEUE}' & '{RABBITMQ_NEWS_SENTIMENT_QUEUE}'. "
        "Press CTRL+C to exit."
    )
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        channel.stop_consuming()
        connection.close()

if __name__ == "__main__":
    main()
