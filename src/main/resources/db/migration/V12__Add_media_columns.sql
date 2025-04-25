-- This migration is now a no-op since its functionality is already covered in V11
-- We're keeping this file to maintain the migration history sequence

-- Original code (commented out):
-- ALTER TABLE message DROP COLUMN IF EXISTS image;
-- ALTER TABLE message ADD COLUMN IF NOT EXISTS media_type TEXT;
-- ALTER TABLE message ADD COLUMN IF NOT EXISTS media_url TEXT; 