CREATE INDEX idx_product_public_list_latest
    ON product (status, hidden, deleted_at, created_at DESC, id DESC);
