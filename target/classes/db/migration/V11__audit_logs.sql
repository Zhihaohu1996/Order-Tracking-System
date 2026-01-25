CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  username VARCHAR(100) NULL,
  role VARCHAR(50) NULL,
  action VARCHAR(80) NOT NULL,
  target VARCHAR(255) NULL,
  status VARCHAR(20) NOT NULL,
  ip VARCHAR(64) NULL,
  details TEXT NULL,
  PRIMARY KEY (id),
  INDEX idx_audit_logs_created_at (created_at),
  INDEX idx_audit_logs_username (username),
  INDEX idx_audit_logs_action (action),
  INDEX idx_audit_logs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
