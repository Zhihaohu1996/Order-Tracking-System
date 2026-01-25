-- Align schema with JPA entities

-- 1) sales_order.created_at is required by SalesOrder entity
--    (Already created in V1 init; no change needed here.)

-- 2) MaterialAssessment entity uses column name 'note'
--    Older schema used 'remark'. Rename it to 'note' (keeps existing data).
ALTER TABLE material_assessment
  CHANGE COLUMN remark note VARCHAR(500) NULL;
