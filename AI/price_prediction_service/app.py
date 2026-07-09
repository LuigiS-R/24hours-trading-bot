from typing import List
import os
import json

import joblib
import numpy as np
import tensorflow as tf
from fastapi import FastAPI, HTTPException
from huggingface_hub import hf_hub_download
from pydantic import BaseModel


REPO_ID = "andreamena/price-prediction-lstm-stock-news"

MODEL_FILE = "price_prediction_lstm_v1.keras"
PRICE_SCALER_FILE = "price_scaler_v1.pkl"
NEWS_SCALER_FILE = "news_scaler_v1.pkl"
METADATA_FILE = "metadata.json"


app = FastAPI(
    title="Price Prediction Service",
    description="Multi-input LSTM service using historical price sequences and news impact features.",
    version="1.0.0"
)


model_path = hf_hub_download(repo_id=REPO_ID, filename=MODEL_FILE)
price_scaler_path = hf_hub_download(repo_id=REPO_ID, filename=PRICE_SCALER_FILE)
news_scaler_path = hf_hub_download(repo_id=REPO_ID, filename=NEWS_SCALER_FILE)
metadata_path = hf_hub_download(repo_id=REPO_ID, filename=METADATA_FILE)

model = tf.keras.models.load_model(model_path)
price_scaler = joblib.load(price_scaler_path)
news_scaler = joblib.load(news_scaler_path)

with open(metadata_path, "r") as f:
    metadata = json.load(f)


class PriceBar(BaseModel):
    Open: float
    High: float
    Low: float
    Close: float
    Volume: float
    Return: float
    MA_5: float
    MA_20: float
    Volatility_5: float
    RSI_14: float
    MACD: float


class NewsPrediction(BaseModel):
    direction: str
    label: int
    confidence: float


class PricePredictionRequest(BaseModel):
    ticker: str
    priceSequence: List[PriceBar]
    newsPrediction: NewsPrediction


@app.get("/")
def root():
    return {
        "service": "Price Prediction Service",
        "status": "running",
        "model": metadata
    }


@app.post("/predict")
def predict_price_movement(request: PricePredictionRequest):
    sequence_length = metadata.get("sequence_length", 20)

    if len(request.priceSequence) != sequence_length:
        raise HTTPException(
            status_code=400,
            detail=f"priceSequence must contain exactly {sequence_length} rows."
        )

    price_features = metadata["price_features"]

    price_array = np.array([
        [getattr(bar, feature) for feature in price_features]
        for bar in request.priceSequence
    ], dtype=np.float32)

    news_sentiment = 1 if request.newsPrediction.label == 1 else -1
    impact_confidence = request.newsPrediction.confidence
    weighted_news_impact = news_sentiment * impact_confidence

    news_array = np.array([[
        news_sentiment,
        impact_confidence,
        weighted_news_impact
    ]], dtype=np.float32)

    price_array_2d = price_array.reshape(-1, len(price_features))
    price_scaled = price_scaler.transform(price_array_2d).reshape(
        1,
        sequence_length,
        len(price_features)
    )

    news_scaled = news_scaler.transform(news_array)

    probability = float(model.predict([price_scaled, news_scaled])[0][0])

    direction = "UP" if probability >= 0.5 else "DOWN"
    label = 1 if probability >= 0.5 else 0
    confidence = probability if label == 1 else 1 - probability

    return {
        "ticker": request.ticker.upper(),
        "prediction": {
            "direction": direction,
            "label": label,
            "probabilityUp": round(probability, 4),
            "confidence": round(confidence, 4)
        },
        "model": {
            "name": metadata.get("model_name", "price-prediction-lstm"),
            "version": metadata.get("version", "1.0")
        }
    }