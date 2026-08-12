import json
import os
import threading
import time

import pika
from fastapi import FastAPI
from pydantic import BaseModel
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    pipeline,
)


# --------------------------------------------------
# Model configuration
# --------------------------------------------------

MODEL_NAME = "andreamena/finbert-stock-news-impact-v10"


# --------------------------------------------------
# RabbitMQ configuration
# --------------------------------------------------

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))

# Existing News Ingestion Service RabbitMQ setup
INPUT_EXCHANGE = "financial-news-exchange"
INPUT_QUEUE = "financial-news"
INPUT_ROUTING_KEY = "financial-news"

# Sentiment Service output
OUTPUT_EXCHANGE = "news-sentiment-exchange"
OUTPUT_QUEUE = "news-sentiment"
OUTPUT_ROUTING_KEY = "news-sentiment"


# --------------------------------------------------
# FastAPI
# --------------------------------------------------

app = FastAPI(
    title="News Impact Prediction Service",
    description=(
        "Fine-tuned FinBERT service for predicting stock-news impact "
        "and processing financial news through RabbitMQ."
    ),
    version="2.0.0",
)


# --------------------------------------------------
# Load model
# --------------------------------------------------

tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)

model = AutoModelForSequenceClassification.from_pretrained(
    MODEL_NAME
)

classifier = pipeline(
    "text-classification",
    model=model,
    tokenizer=tokenizer,
)


# --------------------------------------------------
# Request schema
# --------------------------------------------------

class NewsRequest(BaseModel):
    ticker: str
    headline: str


# --------------------------------------------------
# Shared prediction function
# --------------------------------------------------

def run_prediction(ticker: str, headline: str):
    ticker = ticker.upper().strip()
    headline = headline.strip()

    # V10 was trained using ticker context + headline
    model_input = f"{ticker} [SEP] {headline}"

    result = classifier(model_input)[0]

    label = result["label"]
    confidence = float(result["score"])

    if label == "LABEL_1":
        direction = "UP"
        numeric_label = 1
    else:
        direction = "DOWN"
        numeric_label = 0

    return {
        "ticker": ticker,
        "headline": headline,
        "prediction": {
            "direction": direction,
            "label": numeric_label,
            "confidence": round(confidence, 4),
        },
        "model": {
            "name": "FinBERT-EventRet",
            "version": "10.0",
        },
    }


# --------------------------------------------------
# REST endpoints
# --------------------------------------------------

@app.get("/")
def root():
    return {
        "service": "News Impact Prediction Service",
        "status": "running",
        "model": MODEL_NAME,
        "rabbitmq": {
            "inputQueue": INPUT_QUEUE,
            "outputQueue": OUTPUT_QUEUE,
        },
    }


@app.post("/predict")
def predict_news_impact(request: NewsRequest):
    return run_prediction(
        ticker=request.ticker,
        headline=request.headline,
    )


# --------------------------------------------------
# RabbitMQ setup
# --------------------------------------------------

def create_rabbitmq_connection():
    return pika.BlockingConnection(
        pika.ConnectionParameters(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            heartbeat=60,
            blocked_connection_timeout=300,
        )
    )


def rabbitmq_consumer():
    while True:
        try:
            connection = create_rabbitmq_connection()
            channel = connection.channel()

            # Match the News Ingestion Service setup
            channel.exchange_declare(
                exchange=INPUT_EXCHANGE,
                exchange_type="direct",
                durable=True,
            )

            channel.queue_declare(
                queue=INPUT_QUEUE,
                durable=True,
            )

            channel.queue_bind(
                exchange=INPUT_EXCHANGE,
                queue=INPUT_QUEUE,
                routing_key=INPUT_ROUTING_KEY,
            )

            # Declare our sentiment output
            channel.exchange_declare(
                exchange=OUTPUT_EXCHANGE,
                exchange_type="direct",
                durable=True,
            )

            channel.queue_declare(
                queue=OUTPUT_QUEUE,
                durable=True,
            )

            channel.queue_bind(
                exchange=OUTPUT_EXCHANGE,
                queue=OUTPUT_QUEUE,
                routing_key=OUTPUT_ROUTING_KEY,
            )

            # Process one news item at a time
            channel.basic_qos(
                prefetch_count=1
            )

            print(
                f"Waiting for messages from RabbitMQ queue "
                f"'{INPUT_QUEUE}'..."
            )

            def callback(ch, method, properties, body):
                try:
                    message = json.loads(
                        body.decode("utf-8")
                    )

                    ticker = message["ticker"]
                    headline = message["headline"]

                    prediction = run_prediction(
                        ticker=ticker,
                        headline=headline,
                    )

                    ch.basic_publish(
                        exchange=OUTPUT_EXCHANGE,
                        routing_key=OUTPUT_ROUTING_KEY,
                        body=json.dumps(prediction),
                        properties=pika.BasicProperties(
                            content_type="application/json",
                            delivery_mode=2,
                        ),
                    )

                    print(
                        f"{ticker} | "
                        f"{prediction['prediction']['direction']} | "
                        f"{prediction['prediction']['confidence']}"
                    )

                    ch.basic_ack(
                        delivery_tag=method.delivery_tag
                    )

                except Exception as e:
                    print(
                        "Error processing RabbitMQ message:",
                        e
                    )

                    ch.basic_nack(
                        delivery_tag=method.delivery_tag,
                        requeue=False,
                    )

            channel.basic_consume(
                queue=INPUT_QUEUE,
                on_message_callback=callback,
            )

            channel.start_consuming()

        except Exception as e:
            print(
                "RabbitMQ connection error:",
                e
            )

            print(
                "Retrying connection in 5 seconds..."
            )

            time.sleep(5)


# --------------------------------------------------
# Start RabbitMQ consumer with FastAPI
# --------------------------------------------------

@app.on_event("startup")
def startup_event():
    rabbit_thread = threading.Thread(
        target=rabbitmq_consumer,
        daemon=True,
    )

    rabbit_thread.start()