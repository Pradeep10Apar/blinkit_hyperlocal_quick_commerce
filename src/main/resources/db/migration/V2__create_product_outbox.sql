CREATE TABLE IF NOT EXISTS product_outbox (
  id            UUID PRIMARY KEY,
  aggregate_id  UUID NOT NULL,
  event_type    VARCHAR(60) NOT NULL,
  payload       JSONB NOT NULL,
  status        VARCHAR(20) NOT NULL,
  attempts      INT NOT NULL DEFAULT 0,
  created_at    TIMESTAMP NOT NULL,
  updated_at    TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON product_outbox(status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON product_outbox(aggregate_id);
