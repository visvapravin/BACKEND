-- Flyway Migration: V12__remove_phone_number
-- Description: Drop the phone_number column from the users table.

ALTER TABLE users DROP COLUMN IF EXISTS phone_number;
