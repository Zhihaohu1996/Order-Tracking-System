-- V3: workflow tables for production/shipping

-- 1) Production plan (PMC)
CREATE TABLE production_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  planned_start_date DATE NULL,
  planned_end_date DATE NULL,
  planned_ship_date DATE NULL,
  note VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_plan_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE CASCADE,
  UNIQUE KEY uk_plan_order (order_id)
);

-- 2) Material assessment: attach to order (nullable to keep compatibility with old rows)
ALTER TABLE material_assessment
  ADD COLUMN order_id BIGINT NULL;

CREATE INDEX idx_material_order ON material_assessment(order_id);

ALTER TABLE material_assessment
  ADD CONSTRAINT fk_material_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE CASCADE;

-- 3) Order process progress (Production)
CREATE TABLE order_process (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  process_name VARCHAR(100) NOT NULL,
  target_quantity INT NULL,
  finished_quantity INT NOT NULL DEFAULT 0,
  note VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_process_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE CASCADE
);

CREATE INDEX idx_process_order ON order_process(order_id);

-- 4) Warehouse receipt (finished goods)
CREATE TABLE warehouse_receipt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  received_by VARCHAR(50),
  note VARCHAR(500),
  CONSTRAINT fk_receipt_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE CASCADE,
  UNIQUE KEY uk_receipt_order (order_id)
);

-- 5) Shipment
CREATE TABLE shipment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  planned_ship_date DATE NULL,
  shipped_at DATETIME NULL,
  confirmed_by VARCHAR(50),
  note VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_ship_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE CASCADE,
  UNIQUE KEY uk_ship_order (order_id)
);
