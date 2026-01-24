-- V4: material_assessment audit columns
-- MySQL does NOT support "ADD COLUMN IF NOT EXISTS", so keep this migration simple.
-- If you previously ran a broken V4, drop & recreate the schema (or remove the columns) before rerunning.

ALTER TABLE material_assessment
  ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
