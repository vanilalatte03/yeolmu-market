CREATE TABLE IF NOT EXISTS product_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    url TEXT NOT NULL,
    is_thumbnail BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    thumbnail_product_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN is_thumbnail THEN product_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_image_product
        FOREIGN KEY (product_id) REFERENCES product (id),
    UNIQUE KEY uk_product_image_thumbnail_product (thumbnail_product_id),
    INDEX idx_product_image_product_created (product_id, created_at, id)
);
