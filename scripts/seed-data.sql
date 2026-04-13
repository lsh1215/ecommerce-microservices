-- Seed data for local development
-- Prerequisites: All 4 services started at least once (JPA ddl-auto=update creates tables)
-- Run via: ./scripts/seed-data.sh
-- Idempotent: uses INSERT IGNORE to skip duplicates

-- =============================================================================
-- ecommerce_product
-- =============================================================================
USE ecommerce_product;

-- Brands
INSERT IGNORE INTO brand (id, name, description, logo_url, country, created_at, updated_at) VALUES
(1,  'Nike',            'American athletic footwear and apparel corporation',        NULL, 'US', NOW(), NOW()),
(2,  'Adidas',          'German multinational sportswear corporation',               NULL, 'DE', NOW(), NOW()),
(3,  'Uniqlo',          'Japanese casual wear designer and retailer',                NULL, 'JP', NOW(), NOW()),
(4,  'Zara',            'Spanish fast-fashion retail chain',                         NULL, 'ES', NOW(), NOW()),
(5,  'Levi''s',         'American denim and casual wear brand',                      NULL, 'US', NOW(), NOW()),
(6,  'Ralph Lauren',    'American luxury fashion company',                           NULL, 'US', NOW(), NOW()),
(7,  'H&M',             'Swedish multinational clothing retail company',             NULL, 'SE', NOW(), NOW()),
(8,  'Patagonia',       'American outdoor clothing company',                         NULL, 'US', NOW(), NOW()),
(9,  'The North Face',  'American outdoor recreation products company',              NULL, 'US', NOW(), NOW()),
(10, 'Muji',            'Japanese retail company selling household items and apparel', NULL, 'JP', NOW(), NOW());

-- Products (3 per brand = 30 products)
-- Nike products
INSERT IGNORE INTO product (id, brand_id, name, description, price, status, category, created_at, updated_at) VALUES
(1,  1, 'Essential Cotton Crew Tee',      'Classic crew neck tee in premium cotton',                   29900, 'ACTIVE', 'T-Shirts',   NOW(), NOW()),
(2,  1, 'Dri-FIT Training Shorts',        'Lightweight shorts with moisture-wicking technology',       49900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(3,  1, 'Windrunner Jacket',              'Iconic windbreaker with chevron design',                    129900, 'ACTIVE', 'Outerwear', NOW(), NOW()),
-- Adidas products
(4,  2, 'Trefoil Logo Hoodie',            'Classic fleece hoodie with iconic three-stripe detail',    89900, 'ACTIVE', 'Outerwear',  NOW(), NOW()),
(5,  2, 'Slim Fit Track Pants',           'Tapered track pants with side stripe',                     69900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(6,  2, 'Stan Smith Canvas Tote',         'Minimalist canvas tote inspired by the iconic sneaker',    39900, 'ACTIVE', 'Bags',       NOW(), NOW()),
-- Uniqlo products
(7,  3, 'Supima Cotton Oxford Shirt',     'Premium long-staple cotton button-down shirt',             49900, 'ACTIVE', 'Shirts',     NOW(), NOW()),
(8,  3, 'Slim Fit Chinos Navy',           'Tailored chino trousers in stretch cotton',                59900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(9,  3, 'Ultra Light Down Jacket',        'Packable down jacket with 90% goose down fill',            99900, 'ACTIVE', 'Outerwear',  NOW(), NOW()),
-- Zara products
(10, 4, 'Oversized Linen Blazer',         'Relaxed-fit linen blazer for casual occasions',            129900, 'ACTIVE', 'Outerwear', NOW(), NOW()),
(11, 4, 'High Rise Straight Jeans',       'Classic high-waist straight-leg denim',                    89900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(12, 4, 'Printed Floral Midi Dress',      'Relaxed midi dress with all-over floral print',            79900, 'ACTIVE', 'Shirts',     NOW(), NOW()),
-- Levi's products
(13, 5, '501 Original Fit Jeans',         'The original straight fit jean, since 1873',               99900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(14, 5, 'Classic Trucker Jacket',         'Iconic denim jacket with signature Levi''s details',       119900, 'ACTIVE', 'Outerwear', NOW(), NOW()),
(15, 5, 'Standard Crew Neck Tee',         'Essential cotton tee with small Batwing logo',             29900, 'ACTIVE', 'T-Shirts',   NOW(), NOW()),
-- Ralph Lauren products
(16, 6, 'Classic Fit Oxford Shirt',       'Timeless button-down in pure cotton Oxford cloth',         89900, 'ACTIVE', 'Shirts',     NOW(), NOW()),
(17, 6, 'Polo Bear Knit Sweater',         'Cotton sweater with embroidered Polo Bear motif',          149900, 'ACTIVE', 'Outerwear', NOW(), NOW()),
(18, 6, 'Chino Slim Fit Trousers',        'Slim-fit chinos in stretch cotton twill',                  99900, 'ACTIVE', 'Pants',      NOW(), NOW()),
-- H&M products
(19, 7, 'Regular Fit Linen Shirt',        'Airy linen shirt in a relaxed regular fit',               39900, 'ACTIVE', 'Shirts',     NOW(), NOW()),
(20, 7, 'Skinny High Waist Jeans',        'Ankle-length skinny jeans in stretch denim',              49900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(21, 7, 'Ribbed Knit Beanie',             'Soft ribbed knit beanie in recycled polyester',            19900, 'ACTIVE', 'Accessories', NOW(), NOW()),
-- Patagonia products
(22, 8, 'Better Sweater Fleece Jacket',   'Fleece jacket made from recycled polyester',               199900, 'ACTIVE', 'Outerwear', NOW(), NOW()),
(23, 8, 'Baggies Shorts 5in',             'Quick-drying trail shorts in recycled nylon',             79900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(24, 8, 'Capilene Cool Daily Shirt',      'Lightweight base layer shirt with 50+ UPF',               69900, 'ACTIVE', 'Shirts',     NOW(), NOW()),
-- The North Face products
(25, 9, 'Nuptse 700 Fill Down Jacket',    'Expedition-grade puffer jacket with 700-fill goose down', 299900, 'ACTIVE', 'Outerwear', NOW(), NOW()),
(26, 9, 'Paramount Trail Convertible',    'Convertible pants that zip off into shorts',               99900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(27, 9, 'Simple Dome Tee',                'Organic cotton tee with classic Half Dome logo',          39900, 'ACTIVE', 'T-Shirts',   NOW(), NOW()),
-- Muji products
(28, 10, 'French Linen Crew Neck Shirt',  'Breathable French linen shirt in relaxed fit',            59900, 'ACTIVE', 'Shirts',     NOW(), NOW()),
(29, 10, 'Double Weave Chino Trousers',   'Easy-care chinos with slight stretch',                    69900, 'ACTIVE', 'Pants',      NOW(), NOW()),
(30, 10, 'Organic Cotton Tote Bag',       'Simple tote in heavyweight organic cotton canvas',         24900, 'ACTIVE', 'Bags',       NOW(), NOW());

-- Product variants (3 per product = 90 variants)
-- Nike product 1: Essential Cotton Crew Tee
INSERT IGNORE INTO product_variant (id, product_id, sku, size, color, stock_quantity, price, created_at, updated_at) VALUES
(1,  1,  'NK-ECT-BLK-S',  'S',  'Black', 120, NULL, NOW(), NOW()),
(2,  1,  'NK-ECT-WHT-M',  'M',  'White', 150, NULL, NOW(), NOW()),
(3,  1,  'NK-ECT-NVY-L',  'L',  'Navy',  100, NULL, NOW(), NOW()),
-- Nike product 2: Dri-FIT Training Shorts
(4,  2,  'NK-DFS-BLK-S',  'S',  'Black', 80,  NULL, NOW(), NOW()),
(5,  2,  'NK-DFS-GRY-M',  'M',  'Gray',  90,  NULL, NOW(), NOW()),
(6,  2,  'NK-DFS-NVY-L',  'L',  'Navy',  70,  NULL, NOW(), NOW()),
-- Nike product 3: Windrunner Jacket
(7,  3,  'NK-WRJ-BLK-S',  'S',  'Black', 50,  NULL, NOW(), NOW()),
(8,  3,  'NK-WRJ-RED-M',  'M',  'Red',   60,  NULL, NOW(), NOW()),
(9,  3,  'NK-WRJ-NVY-L',  'L',  'Navy',  55,  NULL, NOW(), NOW()),
-- Adidas product 4: Trefoil Logo Hoodie
(10, 4,  'AD-TLH-BLK-M',  'M',  'Black', 100, NULL, NOW(), NOW()),
(11, 4,  'AD-TLH-GRY-L',  'L',  'Gray',  110, NULL, NOW(), NOW()),
(12, 4,  'AD-TLH-WHT-XL', 'XL', 'White', 90,  NULL, NOW(), NOW()),
-- Adidas product 5: Slim Fit Track Pants
(13, 5,  'AD-STP-BLK-S',  'S',  'Black', 75,  NULL, NOW(), NOW()),
(14, 5,  'AD-STP-NVY-M',  'M',  'Navy',  85,  NULL, NOW(), NOW()),
(15, 5,  'AD-STP-BLK-L',  'L',  'Black', 70,  NULL, NOW(), NOW()),
-- Adidas product 6: Stan Smith Canvas Tote
(16, 6,  'AD-SST-WHT-OS', 'OS', 'White', 200, NULL, NOW(), NOW()),
(17, 6,  'AD-SST-BGE-OS', 'OS', 'Beige', 180, NULL, NOW(), NOW()),
(18, 6,  'AD-SST-GRN-OS', 'OS', 'Green', 160, NULL, NOW(), NOW()),
-- Uniqlo product 7: Supima Cotton Oxford Shirt
(19, 7,  'UQ-SCO-WHT-S',  'S',  'White', 130, NULL, NOW(), NOW()),
(20, 7,  'UQ-SCO-BLU-M',  'M',  'Blue',  140, NULL, NOW(), NOW()),
(21, 7,  'UQ-SCO-BGE-L',  'L',  'Beige', 120, NULL, NOW(), NOW()),
-- Uniqlo product 8: Slim Fit Chinos Navy
(22, 8,  'UQ-SFC-NVY-S',  'S',  'Navy',  95,  NULL, NOW(), NOW()),
(23, 8,  'UQ-SFC-KHA-M',  'M',  'Beige', 105, NULL, NOW(), NOW()),
(24, 8,  'UQ-SFC-BLK-L',  'L',  'Black', 90,  NULL, NOW(), NOW()),
-- Uniqlo product 9: Ultra Light Down Jacket
(25, 9,  'UQ-ULD-BLK-S',  'S',  'Black', 60,  NULL, NOW(), NOW()),
(26, 9,  'UQ-ULD-NVY-M',  'M',  'Navy',  65,  NULL, NOW(), NOW()),
(27, 9,  'UQ-ULD-RED-L',  'L',  'Red',   55,  NULL, NOW(), NOW()),
-- Zara product 10: Oversized Linen Blazer
(28, 10, 'ZR-OLB-BGE-S',  'S',  'Beige', 50,  NULL, NOW(), NOW()),
(29, 10, 'ZR-OLB-WHT-M',  'M',  'White', 55,  NULL, NOW(), NOW()),
(30, 10, 'ZR-OLB-BLK-L',  'L',  'Black', 45,  NULL, NOW(), NOW()),
-- Zara product 11: High Rise Straight Jeans
(31, 11, 'ZR-HRJ-BLU-XS', 'XS', 'Blue',  70,  NULL, NOW(), NOW()),
(32, 11, 'ZR-HRJ-BLU-S',  'S',  'Blue',  80,  NULL, NOW(), NOW()),
(33, 11, 'ZR-HRJ-BLK-M',  'M',  'Black', 75,  NULL, NOW(), NOW()),
-- Zara product 12: Printed Floral Midi Dress
(34, 12, 'ZR-PFD-MUL-XS', 'XS', 'Multi', 60,  NULL, NOW(), NOW()),
(35, 12, 'ZR-PFD-MUL-S',  'S',  'Multi', 70,  NULL, NOW(), NOW()),
(36, 12, 'ZR-PFD-MUL-M',  'M',  'Multi', 65,  NULL, NOW(), NOW()),
-- Levi's product 13: 501 Original Fit Jeans
(37, 13, 'LV-501-IND-30', 'S',  'Indigo', 100, NULL, NOW(), NOW()),
(38, 13, 'LV-501-IND-32', 'M',  'Indigo', 110, NULL, NOW(), NOW()),
(39, 13, 'LV-501-BLK-32', 'M',  'Black',  90,  NULL, NOW(), NOW()),
-- Levi's product 14: Classic Trucker Jacket
(40, 14, 'LV-CTJ-IND-S',  'S',  'Indigo', 55,  NULL, NOW(), NOW()),
(41, 14, 'LV-CTJ-IND-M',  'M',  'Indigo', 60,  NULL, NOW(), NOW()),
(42, 14, 'LV-CTJ-BLK-L',  'L',  'Black',  50,  NULL, NOW(), NOW()),
-- Levi's product 15: Standard Crew Neck Tee
(43, 15, 'LV-SCT-WHT-S',  'S',  'White', 120, NULL, NOW(), NOW()),
(44, 15, 'LV-SCT-GRY-M',  'M',  'Gray',  130, NULL, NOW(), NOW()),
(45, 15, 'LV-SCT-BLK-L',  'L',  'Black', 115, NULL, NOW(), NOW()),
-- Ralph Lauren product 16: Classic Fit Oxford Shirt
(46, 16, 'RL-CFO-WHT-S',  'S',  'White', 75,  NULL, NOW(), NOW()),
(47, 16, 'RL-CFO-BLU-M',  'M',  'Blue',  80,  NULL, NOW(), NOW()),
(48, 16, 'RL-CFO-PNK-L',  'L',  'Pink',  70,  NULL, NOW(), NOW()),
-- Ralph Lauren product 17: Polo Bear Knit Sweater
(49, 17, 'RL-PBK-CRM-S',  'S',  'Beige', 45,  NULL, NOW(), NOW()),
(50, 17, 'RL-PBK-NVY-M',  'M',  'Navy',  50,  NULL, NOW(), NOW()),
(51, 17, 'RL-PBK-CRM-L',  'L',  'Beige', 42,  NULL, NOW(), NOW()),
-- Ralph Lauren product 18: Chino Slim Fit Trousers
(52, 18, 'RL-CSF-KHA-S',  'S',  'Beige', 65,  NULL, NOW(), NOW()),
(53, 18, 'RL-CSF-NVY-M',  'M',  'Navy',  70,  NULL, NOW(), NOW()),
(54, 18, 'RL-CSF-STN-L',  'L',  'Gray',  60,  NULL, NOW(), NOW()),
-- H&M product 19: Regular Fit Linen Shirt
(55, 19, 'HM-RFL-WHT-S',  'S',  'White', 150, NULL, NOW(), NOW()),
(56, 19, 'HM-RFL-BGE-M',  'M',  'Beige', 160, NULL, NOW(), NOW()),
(57, 19, 'HM-RFL-BLU-L',  'L',  'Blue',  140, NULL, NOW(), NOW()),
-- H&M product 20: Skinny High Waist Jeans
(58, 20, 'HM-SHJ-BLU-XS', 'XS', 'Blue',  85,  NULL, NOW(), NOW()),
(59, 20, 'HM-SHJ-BLU-S',  'S',  'Blue',  95,  NULL, NOW(), NOW()),
(60, 20, 'HM-SHJ-BLK-M',  'M',  'Black', 80,  NULL, NOW(), NOW()),
-- H&M product 21: Ribbed Knit Beanie
(61, 21, 'HM-RKB-BLK-OS', 'OS', 'Black', 200, NULL, NOW(), NOW()),
(62, 21, 'HM-RKB-GRY-OS', 'OS', 'Gray',  190, NULL, NOW(), NOW()),
(63, 21, 'HM-RKB-NVY-OS', 'OS', 'Navy',  185, NULL, NOW(), NOW()),
-- Patagonia product 22: Better Sweater Fleece Jacket
(64, 22, 'PA-BSF-BLK-S',  'S',  'Black', 52,  NULL, NOW(), NOW()),
(65, 22, 'PA-BSF-GRY-M',  'M',  'Gray',  58,  NULL, NOW(), NOW()),
(66, 22, 'PA-BSF-NVY-L',  'L',  'Navy',  50,  NULL, NOW(), NOW()),
-- Patagonia product 23: Baggies Shorts 5in
(67, 23, 'PA-BGS-BLK-S',  'S',  'Black', 90,  NULL, NOW(), NOW()),
(68, 23, 'PA-BGS-BLU-M',  'M',  'Blue',  100, NULL, NOW(), NOW()),
(69, 23, 'PA-BGS-RED-L',  'L',  'Red',   85,  NULL, NOW(), NOW()),
-- Patagonia product 24: Capilene Cool Daily Shirt
(70, 24, 'PA-CCD-WHT-S',  'S',  'White', 75,  NULL, NOW(), NOW()),
(71, 24, 'PA-CCD-BLU-M',  'M',  'Blue',  80,  NULL, NOW(), NOW()),
(72, 24, 'PA-CCD-GRY-L',  'L',  'Gray',  72,  NULL, NOW(), NOW()),
-- The North Face product 25: Nuptse 700 Fill Down Jacket
(73, 25, 'TNF-NUP-BLK-S', 'S',  'Black', 40,  NULL, NOW(), NOW()),
(74, 25, 'TNF-NUP-RED-M', 'M',  'Red',   45,  NULL, NOW(), NOW()),
(75, 25, 'TNF-NUP-NVY-L', 'L',  'Navy',  38,  NULL, NOW(), NOW()),
-- The North Face product 26: Paramount Trail Convertible
(76, 26, 'TNF-PTC-KHA-S', 'S',  'Beige', 65,  NULL, NOW(), NOW()),
(77, 26, 'TNF-PTC-GRY-M', 'M',  'Gray',  70,  NULL, NOW(), NOW()),
(78, 26, 'TNF-PTC-BLK-L', 'L',  'Black', 62,  NULL, NOW(), NOW()),
-- The North Face product 27: Simple Dome Tee
(79, 27, 'TNF-SDT-WHT-S', 'S',  'White', 110, NULL, NOW(), NOW()),
(80, 27, 'TNF-SDT-GRY-M', 'M',  'Gray',  120, NULL, NOW(), NOW()),
(81, 27, 'TNF-SDT-BLK-L', 'L',  'Black', 105, NULL, NOW(), NOW()),
-- Muji product 28: French Linen Crew Neck Shirt
(82, 28, 'MJ-FLC-WHT-S',  'S',  'White', 100, NULL, NOW(), NOW()),
(83, 28, 'MJ-FLC-BGE-M',  'M',  'Beige', 110, NULL, NOW(), NOW()),
(84, 28, 'MJ-FLC-NVY-L',  'L',  'Navy',  95,  NULL, NOW(), NOW()),
-- Muji product 29: Double Weave Chino Trousers
(85, 29, 'MJ-DWC-BGE-S',  'S',  'Beige', 80,  NULL, NOW(), NOW()),
(86, 29, 'MJ-DWC-GRY-M',  'M',  'Gray',  88,  NULL, NOW(), NOW()),
(87, 29, 'MJ-DWC-BLK-L',  'L',  'Black', 76,  NULL, NOW(), NOW()),
-- Muji product 30: Organic Cotton Tote Bag
(88, 30, 'MJ-OCT-NAT-OS', 'OS', 'Beige', 250, NULL, NOW(), NOW()),
(89, 30, 'MJ-OCT-BLK-OS', 'OS', 'Black', 230, NULL, NOW(), NOW()),
(90, 30, 'MJ-OCT-NVY-OS', 'OS', 'Navy',  220, NULL, NOW(), NOW());

-- =============================================================================
-- ecommerce_customer
-- =============================================================================
USE ecommerce_customer;

-- BCrypt hash of "password123" (cost 10)
-- $2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu

INSERT IGNORE INTO customer (id, email, password_hash, name, phone, created_at, updated_at) VALUES
(1,  'james.smith@example.com',    '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'James Smith',    '010-1001-0001', NOW(), NOW()),
(2,  'emily.johnson@example.com',  '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Emily Johnson',  '010-1001-0002', NOW(), NOW()),
(3,  'michael.williams@example.com','$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Michael Williams','010-1001-0003', NOW(), NOW()),
(4,  'sophia.brown@example.com',   '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Sophia Brown',   '010-1001-0004', NOW(), NOW()),
(5,  'liam.jones@example.com',     '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Liam Jones',     '010-1001-0005', NOW(), NOW()),
(6,  'olivia.garcia@example.com',  '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Olivia Garcia',  '010-1001-0006', NOW(), NOW()),
(7,  'noah.miller@example.com',    '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Noah Miller',    '010-1001-0007', NOW(), NOW()),
(8,  'ava.davis@example.com',      '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Ava Davis',      '010-1001-0008', NOW(), NOW()),
(9,  'william.rodriguez@example.com','$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu','William Rodriguez','010-1001-0009',NOW(), NOW()),
(10, 'isabella.martinez@example.com','$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu','Isabella Martinez','010-1001-0010',NOW(), NOW()),
(11, 'oliver.hernandez@example.com','$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu','Oliver Hernandez','010-1001-0011',NOW(), NOW()),
(12, 'mia.lopez@example.com',      '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Mia Lopez',      '010-1001-0012', NOW(), NOW()),
(13, 'elijah.gonzalez@example.com','$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Elijah Gonzalez','010-1001-0013', NOW(), NOW()),
(14, 'amelia.wilson@example.com',  '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Amelia Wilson',  '010-1001-0014', NOW(), NOW()),
(15, 'james.anderson@example.com', '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'James Anderson', '010-1001-0015', NOW(), NOW()),
(16, 'charlotte.thomas@example.com','$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu','Charlotte Thomas','010-1001-0016',NOW(), NOW()),
(17, 'lucas.taylor@example.com',   '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Lucas Taylor',   '010-1001-0017', NOW(), NOW()),
(18, 'harper.moore@example.com',   '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Harper Moore',   '010-1001-0018', NOW(), NOW()),
(19, 'henry.jackson@example.com',  '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Henry Jackson',  '010-1001-0019', NOW(), NOW()),
(20, 'evelyn.martin@example.com',  '$2a$10$dXJ3SW6G7P50lGmMQgel6uVKTqemSQNeVCX5voAdCb.rgIWxkWIyu', 'Evelyn Martin',  '010-1001-0020', NOW(), NOW());

-- Customer addresses (1-2 per customer)
INSERT IGNORE INTO customer_address (id, customer_id, label, recipient_name, phone, zip_code, address1, address2, is_default, created_at, updated_at) VALUES
-- Customer 1: 2 addresses
(1,  1,  'HOME', 'James Smith',     '010-1001-0001', '06100', '123 Gangnam-daero, Gangnam-gu, Seoul',         NULL,            1, NOW(), NOW()),
(2,  1,  'WORK', 'James Smith',     '010-1001-0001', '04524', '100 Namdaemun-ro, Jung-gu, Seoul',             'Floor 12',      0, NOW(), NOW()),
-- Customer 2: 1 address
(3,  2,  'HOME', 'Emily Johnson',   '010-1001-0002', '16680', '55 Pangyo-ro, Bundang-gu, Seongnam',           NULL,            1, NOW(), NOW()),
-- Customer 3: 2 addresses
(4,  3,  'HOME', 'Michael Williams','010-1001-0003', '48058', '200 Haeundae-ro, Haeundae-gu, Busan',          NULL,            1, NOW(), NOW()),
(5,  3,  'OTHER','Michael Williams','010-1001-0003', '48059', '201 Haeundae-ro, Haeundae-gu, Busan',          'Apt 301',       0, NOW(), NOW()),
-- Customer 4: 1 address
(6,  4,  'HOME', 'Sophia Brown',    '010-1001-0004', '61452', '50 Geumnam-ro, Dong-gu, Gwangju',              NULL,            1, NOW(), NOW()),
-- Customer 5: 2 addresses
(7,  5,  'HOME', 'Liam Jones',      '010-1001-0005', '34109', '300 Daejeon-daero, Seo-gu, Daejeon',           NULL,            1, NOW(), NOW()),
(8,  5,  'WORK', 'Liam Jones',      '010-1001-0005', '34134', '10 Doryong-ro, Yuseong-gu, Daejeon',           NULL,            0, NOW(), NOW()),
-- Customer 6: 1 address
(9,  6,  'HOME', 'Olivia Garcia',   '010-1001-0006', '44200', '77 Taehwagang-ro, Nam-gu, Ulsan',              NULL,            1, NOW(), NOW()),
-- Customer 7: 2 addresses
(10, 7,  'HOME', 'Noah Miller',     '010-1001-0007', '21500', '400 Incheon-daero, Yeonsu-gu, Incheon',        NULL,            1, NOW(), NOW()),
(11, 7,  'OTHER','Noah Miller',     '010-1001-0007', '21510', '401 Incheon-daero, Yeonsu-gu, Incheon',        'Suite 200',     0, NOW(), NOW()),
-- Customer 8: 1 address
(12, 8,  'HOME', 'Ava Davis',       '010-1001-0008', '28511', '55 Gongnyong-ro, Seo-gu, Daejeon',             NULL,            1, NOW(), NOW()),
-- Customer 9: 2 addresses
(13, 9,  'HOME', 'William Rodriguez','010-1001-0009','13120', '500 Gyeonggi-daero, Suwon-si, Gyeonggi',       NULL,            1, NOW(), NOW()),
(14, 9,  'WORK', 'William Rodriguez','010-1001-0009','16228', '200 Pangyo-ro, Bundang-gu, Seongnam',           NULL,            0, NOW(), NOW()),
-- Customer 10: 1 address
(15, 10, 'HOME', 'Isabella Martinez','010-1001-0010','05500', '10 Achasan-ro, Gwangjin-gu, Seoul',             NULL,            1, NOW(), NOW()),
-- Customer 11: 2 addresses
(16, 11, 'HOME', 'Oliver Hernandez','010-1001-0011', '07207', '88 Yeouinaru-ro, Yeongdeungpo-gu, Seoul',      NULL,            1, NOW(), NOW()),
(17, 11, 'WORK', 'Oliver Hernandez','010-1001-0011', '07335', '50 Gukjegeumyung-ro, Yeongdeungpo-gu, Seoul',  'Tower A 20F',   0, NOW(), NOW()),
-- Customer 12: 1 address
(18, 12, 'HOME', 'Mia Lopez',       '010-1001-0012', '03051', '33 Bukchon-ro, Jongno-gu, Seoul',              NULL,            1, NOW(), NOW()),
-- Customer 13: 2 addresses
(19, 13, 'HOME', 'Elijah Gonzalez', '010-1001-0013', '10503', '600 Ilsandong-daero, Ilsandong-gu, Goyang',   NULL,            1, NOW(), NOW()),
(20, 13, 'OTHER','Elijah Gonzalez', '010-1001-0013', '10504', '601 Ilsandong-daero, Ilsandong-gu, Goyang',   'Apt 501',       0, NOW(), NOW()),
-- Customer 14: 1 address
(21, 14, 'HOME', 'Amelia Wilson',   '010-1001-0014', '14050', '70 Uijeongbu-ro, Uijeongbu-si, Gyeonggi',     NULL,            1, NOW(), NOW()),
-- Customer 15: 2 addresses
(22, 15, 'HOME', 'James Anderson',  '010-1001-0015', '08501', '150 Sinbanpo-ro, Seocho-gu, Seoul',            NULL,            1, NOW(), NOW()),
(23, 15, 'WORK', 'James Anderson',  '010-1001-0015', '06752', '231 Teheran-ro, Gangnam-gu, Seoul',            'Floor 5',       0, NOW(), NOW()),
-- Customer 16: 1 address
(24, 16, 'HOME', 'Charlotte Thomas','010-1001-0016', '11700', '900 Gyeongchun-ro, Gapyeong-gun, Gyeonggi',   NULL,            1, NOW(), NOW()),
-- Customer 17: 2 addresses
(25, 17, 'HOME', 'Lucas Taylor',    '010-1001-0017', '58128', '20 Jungang-ro, Dong-gu, Gwangju',              NULL,            1, NOW(), NOW()),
(26, 17, 'OTHER','Lucas Taylor',    '010-1001-0017', '58130', '22 Jungang-ro, Dong-gu, Gwangju',              'Apt 102',       0, NOW(), NOW()),
-- Customer 18: 1 address
(27, 18, 'HOME', 'Harper Moore',    '010-1001-0018', '25522', '350 Gangneung-daero, Gangneung-si, Gangwon',   NULL,            1, NOW(), NOW()),
-- Customer 19: 2 addresses
(28, 19, 'HOME', 'Henry Jackson',   '010-1001-0019', '36349', '80 Chunhyang-ro, Wansan-gu, Jeonju',           NULL,            1, NOW(), NOW()),
(29, 19, 'WORK', 'Henry Jackson',   '010-1001-0019', '36372', '100 Baekje-daero, Deokjin-gu, Jeonju',         NULL,            0, NOW(), NOW()),
-- Customer 20: 1 address
(30, 20, 'HOME', 'Evelyn Martin',   '010-1001-0020', '41941', '450 Suseong-ro, Suseong-gu, Daegu',            NULL,            1, NOW(), NOW());
