-- Fix MySQL column type mismatch for Hibernate boolean mapping
-- Hibernate expects BOOLEAN/BIT, but MySQL boolean is often created as TINYINT by default.
ALTER TABLE app_user
  MODIFY enabled BIT(1) NOT NULL DEFAULT b'1';
