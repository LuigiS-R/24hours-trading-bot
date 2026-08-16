import os
import json
import time
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

# In-memory storage for rolling price sequences and latest news per ticker
price_buffer = {}  # { "AAPL": [bar1, bar2, ... bar20] }
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
    """Runs LSTM inference when 20 bars are buffered and publishes prediction."""
    bars = price_buffer.get(ticker, [])
    if len(bars) < 20:
        return  # Wait until full 20-bar sequence is collected

    price_features = metadata["price_features"]
    price_array = np.array([[bar[feat] for feat in price_features] for bar in bars[-20:]], dtype=np.float32)
    
    # Apply time-decay to news
    s_val, s_conf, s_weighted = apply_sentiment_decay(latest_news.get(ticker))
    news_array = np.array([[s_val, s_conf, s_weighted]], dtype=np.float32)

    # Scale inputs
    price_scaled = price_scaler.transform(price_array).reshape(1, 20, len(price_features))
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

    # Publish to predictions queue
    channel.basic_publish(
        exchange="",
        routing_key="predictions",
        body=json.dumps(prediction_payload)
    )
    print(f" [x] Published prediction for {ticker}: {direction} ({confidence*100:.1f}%)")

def main():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT, credentials=credentials))
    channel = connection.channel()

    # Declare queues
    channel.queue_declare(queue="market.ticks", durable=True)
    channel.queue_declare(queue="news.sentiment", durable=True)
    channel.queue_declare(queue="predictions", durable=True)

    def on_market_tick(ch, method, properties, body):
        tick = json.loads(body)
        ticker = tick["ticker"]
        price_buffer.setdefault(ticker, []).append(tick["bar"])
        process_prediction(ticker, ch)
        ch.basic_ack(delivery_tag=method.delivery_tag)

    def on_news_sentiment(ch, method, properties, body):
        news = json.loads(body)
        ticker = news["ticker"]
        latest_news[ticker] = {
            "sentiment": 1 if news.get("label") == 1 else -1,
            "confidence": news.get("confidence", 0.5),
            "timestamp": time.time()
        }
        print(f" [*] Updated sentiment for {ticker}")
        ch.basic_ack(delivery_tag=method.delivery_tag)

    channel.basic_consume(queue="market.ticks", on_message_callback=on_market_tick)
    channel.basic_consume(queue="news.sentiment", on_message_callback=on_news_sentiment)

    print(" [*] Prediction Service listening on 'market.ticks' & 'news.sentiment'. Press CTRL+C to exit.")
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        channel.stop_consuming()
        connection.close()

if __name__ == "__main__":
    main()