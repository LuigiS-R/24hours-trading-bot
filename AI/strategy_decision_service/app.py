"""
Strategy & Decision Engine Service
----------------------------------
Fills section 6.10 of the architecture doc. This service does NOT train any
model. It orchestrates the two existing AI services (News Impact Prediction
Service and Price Prediction Service) and turns their two independent
predictions into a single trade decision.

Core design idea (adapted from a divergence-detection pattern used in a
prior multi-agent LLM project): instead of silently trusting whichever
number comes out last in the pipeline, we explicitly check whether the
news-implied direction and the price-model direction AGREE. If they agree,
we act with full confidence. If they disagree, we don't average them away
-- we downgrade to HOLD and say why, because a blended number would hide
a real signal (two sources pointing in different directions is itself
useful information, not noise).

We also fail closed: if either upstream service is unreachable or errors,
we return HOLD with a reason instead of guessing.
"""

import os
from typing import List, Optional

import httpx
from fastapi import FastAPI
from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

NEWS_IMPACT_SERVICE_URL = os.getenv("NEWS_IMPACT_SERVICE_URL", "http://127.0.0.1:8000")
PRICE_PREDICTION_SERVICE_URL = os.getenv("PRICE_PREDICTION_SERVICE_URL", "http://127.0.0.1:8001")
REQUEST_TIMEOUT_SECONDS = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "5.0"))

# Minimum confidence each upstream service must report before we're willing
# to act on AGREEMENT with full confidence. Below this, even an agreeing
# signal is downgraded.
MIN_CONFIDENCE_THRESHOLD = float(os.getenv("MIN_CONFIDENCE_THRESHOLD", "0.55"))

app = FastAPI(
    title="Strategy & Decision Engine Service",
    description=(
        "Combines News Impact and Price Prediction outputs into a single "
        "trade signal, with explicit agreement/divergence handling."
    ),
    version="1.0.0",
)

# ---------------------------------------------------------------------------
# Schemas (mirrors the upstream services' own request/response shapes)
# ---------------------------------------------------------------------------


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


class DecisionRequest(BaseModel):
    ticker: str
    headline: str
    priceSequence: List[PriceBar] = Field(
        ..., description="Must contain exactly 20 rows (same contract as the Price Prediction Service)."
    )


class NewsPrediction(BaseModel):
    direction: str
    label: int
    confidence: float


class PricePrediction(BaseModel):
    direction: str
    label: int
    probabilityUp: float
    confidence: float


class DecisionResponse(BaseModel):
    ticker: str
    signal: str  # BUY / SELL / HOLD
    confidence: float
    sourcesAgree: bool
    rationale: str
    newsPrediction: Optional[NewsPrediction] = None
    pricePrediction: Optional[PricePrediction] = None
    warnings: List[str] = []


# ---------------------------------------------------------------------------
# Upstream service calls
# ---------------------------------------------------------------------------


def call_news_impact_service(ticker: str, headline: str) -> Optional[dict]:
    """Calls Andrea's News Impact Prediction Service. Returns None on failure
    (fail-closed -- caller must treat None as 'no signal available')."""
    try:
        with httpx.Client(timeout=REQUEST_TIMEOUT_SECONDS) as client:
            resp = client.post(
                f"{NEWS_IMPACT_SERVICE_URL}/predict",
                json={"ticker": ticker, "headline": headline},
            )
            resp.raise_for_status()
            return resp.json()
    except (httpx.HTTPError, httpx.TimeoutException):
        return None


def call_price_prediction_service(
    ticker: str, price_sequence: List[PriceBar], news_prediction: dict
) -> Optional[dict]:
    """Calls Andrea's Price Prediction Service, feeding in the news
    prediction as required by its contract. Returns None on failure."""
    try:
        with httpx.Client(timeout=REQUEST_TIMEOUT_SECONDS) as client:
            resp = client.post(
                f"{PRICE_PREDICTION_SERVICE_URL}/predict",
                json={
                    "ticker": ticker,
                    "priceSequence": [bar.model_dump() for bar in price_sequence],
                    "newsPrediction": {
                        "direction": news_prediction["prediction"]["direction"],
                        "label": news_prediction["prediction"]["label"],
                        "confidence": news_prediction["prediction"]["confidence"],
                    },
                },
            )
            resp.raise_for_status()
            return resp.json()
    except (httpx.HTTPError, httpx.TimeoutException, KeyError):
        return None


# ---------------------------------------------------------------------------
# Decision logic
# ---------------------------------------------------------------------------


def make_decision(news_result: Optional[dict], price_result: Optional[dict]) -> DecisionResponse.__fields__:
    warnings: List[str] = []

    # --- Fail-closed cases: missing data means we don't guess ---
    if news_result is None and price_result is None:
        return dict(
            signal="HOLD",
            confidence=0.0,
            sourcesAgree=False,
            rationale="Both News Impact Service and Price Prediction Service were unavailable. Failing closed.",
            newsPrediction=None,
            pricePrediction=None,
            warnings=["news_service_unavailable", "price_service_unavailable"],
        )

    if price_result is None:
        return dict(
            signal="HOLD",
            confidence=0.0,
            sourcesAgree=False,
            rationale="Price Prediction Service unavailable (it depends on the News Impact output as input). Failing closed rather than trading on news alone.",
            newsPrediction=_extract_news(news_result),
            pricePrediction=None,
            warnings=["price_service_unavailable"],
        )

    if news_result is None:
        # Price service technically requires a news prediction as input, so
        # in practice this branch means the caller couldn't even reach the
        # news service to build that input. We still fail closed.
        return dict(
            signal="HOLD",
            confidence=0.0,
            sourcesAgree=False,
            rationale="News Impact Service unavailable, so the Price Prediction Service could not be called with a valid input. Failing closed.",
            newsPrediction=None,
            pricePrediction=None,
            warnings=["news_service_unavailable"],
        )

    news_pred = _extract_news(news_result)
    price_pred = _extract_price(price_result)

    news_direction = news_pred.direction
    price_direction = price_pred.direction
    agree = news_direction == price_direction

    if not agree:
        # Divergence: don't blend it away, surface it as a low-confidence hold.
        return dict(
            signal="HOLD",
            confidence=round(min(news_pred.confidence, price_pred.confidence), 4),
            sourcesAgree=False,
            rationale=(
                f"News Impact predicts {news_direction} (confidence {news_pred.confidence}) "
                f"but the Price Prediction model predicts {price_direction} "
                f"(confidence {price_pred.confidence}). Sources disagree, so no directional "
                f"trade is taken; flagged as a HOLD pending further confirmation."
            ),
            newsPrediction=news_pred,
            pricePrediction=price_pred,
            warnings=[],
        )

    # Agreement case: both point the same direction.
    combined_confidence = round((news_pred.confidence + price_pred.confidence) / 2, 4)

    if combined_confidence < MIN_CONFIDENCE_THRESHOLD:
        warnings.append("low_combined_confidence")
        return dict(
            signal="HOLD",
            confidence=combined_confidence,
            sourcesAgree=True,
            rationale=(
                f"News and price model agree on {news_direction}, but combined confidence "
                f"({combined_confidence}) is below the minimum threshold "
                f"({MIN_CONFIDENCE_THRESHOLD}). Holding rather than acting on a weak signal."
            ),
            newsPrediction=news_pred,
            pricePrediction=price_pred,
            warnings=warnings,
        )

    signal = "BUY" if news_direction == "UP" else "SELL"
    return dict(
        signal=signal,
        confidence=combined_confidence,
        sourcesAgree=True,
        rationale=(
            f"News Impact and Price Prediction both indicate {news_direction} "
            f"(confidences {news_pred.confidence} and {price_pred.confidence}). "
            f"Combined confidence {combined_confidence} clears the {MIN_CONFIDENCE_THRESHOLD} threshold."
        ),
        newsPrediction=news_pred,
        pricePrediction=price_pred,
        warnings=warnings,
    )


def _extract_news(news_result: dict) -> NewsPrediction:
    p = news_result["prediction"]
    return NewsPrediction(direction=p["direction"], label=p["label"], confidence=p["confidence"])


def _extract_price(price_result: dict) -> PricePrediction:
    p = price_result["prediction"]
    return PricePrediction(
        direction=p["direction"],
        label=p["label"],
        probabilityUp=p["probabilityUp"],
        confidence=p["confidence"],
    )


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@app.get("/")
def root():
    return {
        "service": "Strategy & Decision Engine Service",
        "status": "running",
        "upstream": {
            "news_impact_service": NEWS_IMPACT_SERVICE_URL,
            "price_prediction_service": PRICE_PREDICTION_SERVICE_URL,
        },
        "min_confidence_threshold": MIN_CONFIDENCE_THRESHOLD,
    }


@app.post("/decide", response_model=DecisionResponse)
def decide(request: DecisionRequest):
    if len(request.priceSequence) != 20:
        return DecisionResponse(
            ticker=request.ticker.upper(),
            signal="HOLD",
            confidence=0.0,
            sourcesAgree=False,
            rationale="priceSequence must contain exactly 20 rows. Failing closed.",
            warnings=["invalid_price_sequence_length"],
        )

    news_result = call_news_impact_service(request.ticker, request.headline)
    price_result = None
    if news_result is not None:
        price_result = call_price_prediction_service(request.ticker, request.priceSequence, news_result)

    decision = make_decision(news_result, price_result)
    return DecisionResponse(ticker=request.ticker.upper(), **decision)
