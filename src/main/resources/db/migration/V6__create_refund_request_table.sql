CREATE TABLE refund_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    seller_response VARCHAR(255) NULL,
    requested_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    rejected_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refund_request_order UNIQUE (order_id),
    CONSTRAINT fk_refund_request_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_refund_request_requester FOREIGN KEY (requester_id) REFERENCES users (id)
);
