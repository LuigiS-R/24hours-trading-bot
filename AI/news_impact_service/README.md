# News Impact Prediction Service

## Role in the System

This service is responsible for the News Impact Analysis module of the 24-Hour Trading Bot. It receives a stock ticker and news headline from the backend, predicts the expected market direction using the fine-tuned FinBERT model, and returns the prediction confidence for use in the trading decision pipeline.

## Model

- **Base Model:** ProsusAI/FinBERT
- **Fine-tuned on:** Kaggle Stock News Sentiment Dataset

## Performance

- Accuracy: **65.58%**
- F1 Score: **67.35%**

## Installation

```bash
pip install -r requirements.txt
```

## Run the Service

```bash
uvicorn app:app --reload
```

The service will be available at:

```
http://127.0.0.1:8000/docs
```

## API

### POST `/predict`

### Example Request

```json
{
  "ticker": "AAPL",
  "headline": "Apple reported stronger than expected quarterly earnings."
}
```

### Example Response

```json
{
  "ticker": "AAPL",
  "predictedDirection": "UP",
  "confidence": 0.813,
  "modelVersion": "finbert-eventret-v1"
}
```
