# Strategy Decision Service

Consumes predictions and publishes approved limit orders for the one configured
Alpaca account. It fetches `GET {ACCOUNT_SERVICE_BASE_URL}/internal/v1/portfolio`
without user or service-account authentication. Each approved order has no
per-account routing field; Order Execution always routes it with the installation-wide
`ALPACA_API_KEY` and `ALPACA_API_SECRET`.

RabbitMQ configuration remains environment based. Run with `python main.py`
after setting `ACCOUNT_SERVICE_BASE_URL` and RabbitMQ variables.
