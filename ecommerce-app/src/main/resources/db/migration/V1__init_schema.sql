CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    role            VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE addresses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    line1       VARCHAR(255) NOT NULL,
    line2       VARCHAR(255),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100),
    postal_code VARCHAR(20)  NOT NULL,
    country     VARCHAR(100) NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE categories (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name      VARCHAR(150) NOT NULL,
    parent_id UUID REFERENCES categories(id)
);

CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID REFERENCES categories(id),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    sku         VARCHAR(64) NOT NULL UNIQUE,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE product_images (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE inventory (
    product_id UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    quantity   INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    reserved   INT NOT NULL DEFAULT 0 CHECK (reserved >= 0)
);

CREATE TABLE carts (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id    UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity   INT NOT NULL CHECK (quantity > 0),
    UNIQUE (cart_id, product_id)
);

CREATE TABLE orders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id),
    address_id   UUID NOT NULL REFERENCES addresses(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12,2) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id         UUID NOT NULL REFERENCES products(id),
    quantity           INT NOT NULL CHECK (quantity > 0),
    price_at_purchase  NUMERIC(12,2) NOT NULL
);

CREATE TABLE payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID NOT NULL UNIQUE REFERENCES orders(id),
    provider         VARCHAR(50) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_ref  VARCHAR(255),
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE reviews (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id),
    rating     INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (product_id, user_id)
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_reviews_product ON reviews(product_id);
