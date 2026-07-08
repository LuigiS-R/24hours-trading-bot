from fastapi import FastAPI
from pydantic import BaseModel
from transformers import AutoTokenizer, AutoModelForSequenceClassification, pipeline


MODEL_NAME = "andreamena/finbert-eventret-stock-news"

app = FastAPI(
    title="News Impact Prediction Service",
    description="Fine-tuned FinBERT model for predicting stock news impact.",
    version="1.0.0"
)

tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME)

classifier = pipeline(
    "text-classification",
    model=model,
    tokenizer=tokenizer
)


class NewsRequest(BaseModel):
    ticker: str
    headline: str


@app.get("/")
def root():
    return {
        "service": "News Impact Prediction Service",
        "model": MODEL_NAME,
        "status": "running"
    }


@app.post("/predict")
def predict_news_impact(request: NewsRequest):
    result = classifier(request.headline)[0]

    label = result["label"]
    confidence = float(result["score"])

    # LABEL_1 = positive return, LABEL_0 = negative/zero return
    direction = "UP" if label == "LABEL_1" else "DOWN"

    return {
        "ticker": request.ticker.upper(),
        "headline": request.headline,
        "predictedDirection": direction,
        "confidence": round(confidence, 4),
        "modelVersion": "finbert-eventret-v1"
    }
