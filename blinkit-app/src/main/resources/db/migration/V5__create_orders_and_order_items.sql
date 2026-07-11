-- Orders table
CREATE TABLE orders (
    id              UUID            PRIMARY KEY,
    user_id         VARCHAR(64)     NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PLACED',
    total_amount    NUMERIC(12,2)   NOT NULL,
    delivery_address VARCHAR(500),
    payment_method  VARCHAR(30),
    payment_status  VARCHAR(30)     DEFAULT 'PENDING',
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),
    delivered_at    TIMESTAMP
);

-- Order line-items table
CREATE TABLE order_items (
    id              UUID            PRIMARY KEY,
    order_id        UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      UUID            NOT NULL,
    product_name    VARCHAR(255)    NOT NULL,
    quantity        INT             NOT NULL,
    unit_price      NUMERIC(12,2)   NOT NULL
);
