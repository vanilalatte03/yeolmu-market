CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nickname VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_category_name UNIQUE (name)
);

CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    price INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES users (id)
);

CREATE TABLE wish (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wish_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_wish_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_wish_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE chatroom (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_message_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chatroom_product_buyer_seller UNIQUE (product_id, buyer_id, seller_id),
    CONSTRAINT fk_chatroom_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_chatroom_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_chatroom_buyer FOREIGN KEY (buyer_id) REFERENCES users (id)
);

CREATE TABLE chatmessage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chatroom_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chatmessage_chatroom FOREIGN KEY (chatroom_id) REFERENCES chatroom (id),
    CONSTRAINT fk_chatmessage_sender FOREIGN KEY (sender_id) REFERENCES users (id)
);

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    order_status VARCHAR(20) NOT NULL,
    order_price INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount INT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    paid_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    cancel_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
