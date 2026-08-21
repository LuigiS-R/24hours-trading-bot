# RabbitMQ connection-events contract

Order Execution consumes connection lifecycle events from the durable
`connection.events` queue. These events control the account-scoped Alpaca
trade-update WebSocket registry.

```json
{
  "type": "open",
  "connection_id": "cdb06f0b-e7ea-4a24-832e-23d9d7fd6f64"
}
```

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `type` | string | Yes | `open` creates a stream; `close` closes and removes it. |
| `connection_id` | UUID string | Yes | The Account Service brokerage connection ID. |

An `open` event resolves that connection's OAuth credential through Account
Service and opens one Alpaca trade-update stream. A `close` event closes an
in-progress or active stream and removes its in-memory registry entry.
