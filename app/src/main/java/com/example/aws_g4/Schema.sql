-- Corrected schema for MySQL 8 / Aurora MySQL
-- Creation order: create referenced tables (customers, items) first.

CREATE TABLE customers (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,          -- never plain text: store only a bcrypt/argon2 hash
  email         VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE items (
  id        INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name      VARCHAR(255) NOT NULL,
  image_url VARCHAR(255) NOT NULL,
  price     DECIMAL(8,2) NOT NULL
);

CREATE TABLE orders (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_uuid     CHAR(36) NOT NULL UNIQUE,      -- UUID created by the app (idempotency key, protects against duplicate SQS delivery)
  user_id        BIGINT UNSIGNED NOT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',  -- RECEIVED / PROCESSING / DONE / FAILED
  attempts       INT NOT NULL DEFAULT 0,        -- number of processing attempts (used by the retry demo)
  failure_reason VARCHAR(255) NULL,             -- for operators only; never exposed in the app response
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES customers(id)
);

CREATE TABLE order_items (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id   BIGINT UNSIGNED NOT NULL,
  item_id    INT UNSIGNED NOT NULL,
  quantity   INT UNSIGNED NOT NULL,
  unit_price DECIMAL(8,2) NOT NULL,             -- price at order time (stays correct if items.price changes later)
  CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_oi_item  FOREIGN KEY (item_id)  REFERENCES items(id),
  UNIQUE KEY uq_order_item (order_id, item_id)
);

CREATE TABLE notifications (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT UNSIGNED NOT NULL,
  message    VARCHAR(255) NOT NULL,
  type       VARCHAR(50) NOT NULL,              -- e.g. ORDER_DONE / ORDER_FAILED
  is_read    TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES customers(id)
);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_notif_user_created  ON notifications (user_id, created_at DESC);