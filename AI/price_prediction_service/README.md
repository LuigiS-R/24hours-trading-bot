# Price Prediction Service

This microservice predicts future stock price direction using historical market data and the output from the News Impact Prediction Service.

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

Then run the service:

```bash
cd AI/price_prediction_service
uvicorn app:app --reload --port 8001
```

The API documentation will be available at:

```text
http://127.0.0.1:8001/docs
```

## API

### POST `/predict`

### Example Request

```json
{
  "ticker": "AAPL",
  "priceSequence": [
    {
      "Open": 180.0,
      "High": 182.0,
      "Low": 179.0,
      "Close": 181.0,
      "Volume": 50000000,
      "Return": 0.01,
      "MA_5": 179.5,
      "MA_20": 175.2,
      "Volatility_5": 0.02,
      "RSI_14": 61.5,
      "MACD": 1.2
    }
  ],
  "newsPrediction": {
    "direction": "UP",
    "label": 1,
    "confidence": 0.813
  }
}
```

> `priceSequence` must contain exactly **20 rows**.

### Example Response

```json
{
  "ticker": "AAPL",
  "prediction": {
    "direction": "UP",
    "label": 1,
    "probabilityUp": 0.9742,
    "confidence": 0.9742
  },
  "model": {
    "name": "price-prediction-lstm",
    "version": "1.0"
  }
}
```

## Role in the System

This service is responsible for the Price Prediction module of the 24-Hour Trading Bot. It receives recent market data and the prediction output from the News Impact Service, then returns the predicted stock direction and confidence for the trading decision pipeline.
