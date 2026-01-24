CREATE TABLE IF NOT EXISTS order_photo (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  stored_filename VARCHAR(255) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order_photo_order_id (order_id),
  CONSTRAINT fk_order_photo_order FOREIGN KEY (order_id) REFERENCES sales_order (id) ON DELETE CASCADE
) ENGINE=InnoDB;
