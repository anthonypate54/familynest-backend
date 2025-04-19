-- Check if email column exists before adding it
SET @dbname = DATABASE();
SET @tablename = "app_user";
SET @columnname = "email";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  "SELECT 'Email column already exists'",
  "ALTER TABLE app_user ADD COLUMN email VARCHAR(255) NOT NULL DEFAULT 'temp@example.com'"
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add unique constraint if it doesn't exist
SET @constraintname = "uk_user_email";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (constraint_name = @constraintname)
  ) > 0,
  "SELECT 'Email constraint already exists'",
  "ALTER TABLE app_user ADD CONSTRAINT uk_user_email UNIQUE (email)"
));
PREPARE constraintIfNotExists FROM @preparedStatement;
EXECUTE constraintIfNotExists;
DEALLOCATE PREPARE constraintIfNotExists;

-- Update existing users with a temporary email if they don't have one
UPDATE app_user SET email = CONCAT(username, '@example.com') WHERE email = 'temp@example.com'; 