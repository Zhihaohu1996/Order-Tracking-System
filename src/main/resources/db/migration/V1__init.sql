-- V1: init (orders + items)
CREATE TABLE IF NOT EXISTS sales_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  customer_name VARCHAR(128),
  contact VARCHAR(128),
  currency VARCHAR(16),
  payment_terms VARCHAR(64),
  total_amount DECIMAL(18,2),
  product_req VARCHAR(255),
  packaging_req VARCHAR(255),
  status VARCHAR(32),
  created_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_order_order_no (order_no)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_name VARCHAR(255) NOT NULL,
  spec VARCHAR(255),
  quantity INT,
  unit_price DOUBLE,
  notes VARCHAR(500),
  order_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_items_order_id (order_id),
  CONSTRAINT fk_order_items_order
    FOREIGN KEY (order_id) REFERENCES sales_order(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;
