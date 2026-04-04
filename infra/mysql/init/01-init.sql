CREATE DATABASE IF NOT EXISTS ecommerce_product CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ecommerce_order CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ecommerce_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ecommerce_customer CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON ecommerce_product.* TO 'sa'@'%';
GRANT ALL PRIVILEGES ON ecommerce_order.* TO 'sa'@'%';
GRANT ALL PRIVILEGES ON ecommerce_payment.* TO 'sa'@'%';
GRANT ALL PRIVILEGES ON ecommerce_customer.* TO 'sa'@'%';
FLUSH PRIVILEGES;
