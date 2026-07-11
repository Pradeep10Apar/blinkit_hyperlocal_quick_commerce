-- ============================================================
-- V1: Stock table — tracks inventory per product
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE stock (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id      UUID        NOT NULL UNIQUE,   -- 1:1 with product in blinkit-app
    quantity         INT         NOT NULL DEFAULT 0,
    reserved         INT         NOT NULL DEFAULT 0, -- reserved during order, not yet deducted
    warehouse       VARCHAR(120) NOT NULL DEFAULT 'DEFAULT',
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now()
);

-- Fast lookup by product_id (already UNIQUE, so index is implicit)
-- Index for low-stock alerts
CREATE INDEX idx_stock_low_qty ON stock (quantity) WHERE quantity <= 10;
