-- V8: warehouse receipt logs (allow multiple receipts / partial receipts)

-- Receipt log header (one record per warehouse receiving action)
CREATE TABLE IF NOT EXISTS warehouse_receipt_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  received_by VARCHAR(100) NULL,
  note VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_wrlog_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE CASCADE
);

CREATE INDEX idx_wrlog_order ON warehouse_receipt_log(order_id, received_at);

-- Receipt log items (line items of each receiving action)
CREATE TABLE IF NOT EXISTS warehouse_receipt_log_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receipt_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  qty INT NOT NULL,
  CONSTRAINT fk_wrlog_item_receipt FOREIGN KEY (receipt_id) REFERENCES warehouse_receipt_log(id) ON DELETE CASCADE,
  CONSTRAINT fk_wrlog_item_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id) ON DELETE RESTRICT
);

CREATE INDEX idx_wrlog_item_receipt ON warehouse_receipt_log_item(receipt_id);
CREATE INDEX idx_wrlog_item_order_item ON warehouse_receipt_log_item(order_item_id);
