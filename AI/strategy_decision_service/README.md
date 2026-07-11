# Strategy & Decision Engine Service

This microservice fills section **6.10 (Strategy & Decision Engine)** of the architecture doc. It does not train or serve any model itself — it orchestrates the two existing AI services (News Impact Prediction Service and Price Prediction Service) and converts their two independent predictions into a single, explainable trade decision.

## Role in the System

Right now the News Impact Service and the Price Prediction Service are each independently correct, but nothing connects them into an actual decision — a caller would have to manually call the first, copy its output into the second, and then decide for themselves what to do with two numbers that might not agree. This service closes that gap: it calls both services in the correct order and produces one final signal.

## Core Design Decision: Agreement Check, Not Blending

Instead of averaging the two predictions into a single number, this service explicitly checks whether the News Impact direction and the Price Prediction direction **agree**:

- **Agree + confidence above threshold** → act on it (`BUY` if both say `UP`, `SELL` if both say `DOWN`), with a combined confidence score.
- **Agree but confidence below threshold** → `HOLD`. Both sources point the same way, but not strongly enough to act on.
- **Disagree** → `HOLD`, with the disagreement explained in the rationale rather than hidden inside an averaged number. Two sources pointing in different directions is itself a useful signal, not noise to smooth over.
- **Either upstream service is unreachable** → `HOLD` (fail-closed). The service never fabricates a signal when it's missing real input.

This mirrors a divergence-detection pattern from a prior LLM-agent sentiment analysis project: when two independent analyses conflict, surface the conflict instead of collapsing it into a single blended score.

## Functional Requirements Implemented

- FR-1: Calls the News Impact Service with `{ticker, headline}`.
- FR-2: Feeds the News Impact output into the Price Prediction Service, matching its required `newsPrediction` input contract exactly.
- FR-3: Compares both predicted directions and computes a combined confidence.
- FR-4: Returns `BUY` / `SELL` / `HOLD` with a human-readable rationale explaining the decision.
- FR-5: Fails closed (returns `HOLD`) whenever an upstream service is unreachable, times out, or returns an unexpected shape.

## Configuration

Set these environment variables to point at the real deployed services (defaults assume both run locally, matching their own READMEs):

```
NEWS_IMPACT_SERVICE_URL=http://127.0.0.1:8000
PRICE_PREDICTION_SERVICE_URL=http://127.0.0.1:8001
MIN_CONFIDENCE_THRESHOLD=0.55
REQUEST_TIMEOUT_SECONDS=5.0
```

## Installation

```
pip install -r requirements.txt
```

## Run the Service

Make sure the News Impact Service (port 8000) and Price Prediction Service (port 8001) are already running, then:

```
uvicorn app:app --reload --port 8002
```

The service will be available at:

```
http://127.0.0.1:8002/docs
```

## API

### POST /decide

**Example Request** (see `test_request.json` for a full 20-row example):

```json
{
  "ticker": "AAPL",
  "headline": "Apple reported stronger than expected quarterly earnings.",
  "priceSequence": [ /* exactly 20 PriceBar rows, same schema as the Price Prediction Service */ ]
}
```

**Example Response (sources agree):**

```json
{
  "ticker": "AAPL",
  "signal": "BUY",
  "confidence": 0.8936,
  "sourcesAgree": true,
  "rationale": "News Impact and Price Prediction both indicate UP (confidences 0.813 and 0.9742). Combined confidence 0.8936 clears the 0.55 threshold.",
  "newsPrediction": {"direction": "UP", "label": 1, "confidence": 0.813},
  "pricePrediction": {"direction": "UP", "label": 1, "probabilityUp": 0.9742, "confidence": 0.9742},
  "warnings": []
}
```

**Example Response (sources disagree):**

```json
{
  "ticker": "AAPL",
  "signal": "HOLD",
  "confidence": 0.65,
  "sourcesAgree": false,
  "rationale": "News Impact predicts UP (confidence 0.81) but the Price Prediction model predicts DOWN (confidence 0.65). Sources disagree, so no directional trade is taken; flagged as a HOLD pending further confirmation.",
  "newsPrediction": {"direction": "UP", "label": 1, "confidence": 0.81},
  "pricePrediction": {"direction": "DOWN", "label": 0, "probabilityUp": 0.35, "confidence": 0.65},
  "warnings": []
}
```

## Testing Notes

This was verified end-to-end by running the News Impact and Price Prediction endpoints (mocked with their documented response shapes) alongside this service and confirming: (1) the agree/high-confidence case returns the correct `BUY`/`SELL`, (2) the agree/low-confidence case correctly downgrades to `HOLD`, (3) the disagreement case returns `HOLD` with both predictions surfaced, and (4) service-unavailability cases fail closed to `HOLD` rather than guessing.
