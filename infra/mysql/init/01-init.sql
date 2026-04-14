CREATE DATABASE IF NOT EXISTS ecommerce_product CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ecommerce_order CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ecommerce_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ecommerce_customer CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON ecommerce_product.* TO 'sa'@'%';
GRANT ALL PRIVILEGES ON ecommerce_order.* TO 'sa'@'%';
GRANT ALL PRIVILEGES ON ecommerce_payment.* TO 'sa'@'%';
GRANT ALL PRIVILEGES ON ecommerce_customer.* TO 'sa'@'%';
FLUSH PRIVILEGES;

-- Outbox event table for transactional outbox pattern (each service DB)
USE ecommerce_order;
CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    published_at DATETIME(6) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_outbox_unpublished (published_at, created_at)
);

USE ecommerce_payment;
CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    published_at DATETIME(6) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_outbox_unpublished (published_at, created_at)
);
