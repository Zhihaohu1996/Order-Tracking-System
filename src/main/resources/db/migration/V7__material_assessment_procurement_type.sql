-- V7: add procurement_type column required by MaterialAssessment entity

-- 1) Add column (nullable first to safely backfill)
ALTER TABLE material_assessment
  ADD COLUMN procurement_type VARCHAR(50) NULL;

-- 2) Backfill based on legacy 'source' values (if any rows already exist)
UPDATE material_assessment
SET procurement_type = CASE
  WHEN source = 'PROCURE' THEN 'EXTERNAL_PURCHASE'
  WHEN source = 'STOCK'   THEN 'IN_STOCK'
  ELSE 'EXTERNAL_PURCHASE'
END
WHERE procurement_type IS NULL;

-- 3) Enforce NOT NULL to match JPA (@Column(nullable=false))
ALTER TABLE material_assessment
  MODIFY COLUMN procurement_type VARCHAR(50) NOT NULL;
