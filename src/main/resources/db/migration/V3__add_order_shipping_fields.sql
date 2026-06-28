ALTER TABLE orders
    ADD COLUMN tracking_number VARCHAR(100) NULL,
    ADD COLUMN shipped_at DATETIME NULL;
