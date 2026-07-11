ALTER TABLE product_outbox
  ALTER COLUMN payload TYPE TEXT
  USING payload::text;
