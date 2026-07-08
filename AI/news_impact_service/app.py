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

    # Convert Hugging Face label to integer + direction
    if label == "LABEL_1":
        direction = "UP"
        numeric_label = 1
    else:
        direction = "DOWN"
        numeric_label = 0

    return {
        "ticker": request.ticker.upper(),
        "headline": request.headline,
        "prediction": {
            "direction": direction,
            "label": numeric_label,
            "confidence": round(confidence, 3)
        },
        "model": {
            "name": "FinBERT",
            "version": "1.0"
        }
    }
