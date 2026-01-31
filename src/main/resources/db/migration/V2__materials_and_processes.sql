-- V2: materials and processes (minimal tables for later extension)
CREATE TABLE IF NOT EXISTS material_assessment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  category ENUM('AUX','MAIN','PACK') NOT NULL,
  material_name VARCHAR(128) NOT NULL,
  source ENUM('PROCURE','STOCK') NOT NULL,
  remark VARCHAR(255),
  PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS production_process (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_name VARCHAR(128) NOT NULL,
  planned_qty INT NOT NULL DEFAULT 0,
  completed_qty INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB;
