-- Add thumbnail_url column to message table
ALTER TABLE message ADD COLUMN thumbnail_url text;

-- Add comment to explain the column
COMMENT ON COLUMN message.thumbnail_url IS 'URL path to video thumbnail image';

-- Create index for faster lookups
CREATE INDEX idx_message_thumbnail_url ON message(thumbnail_url);

-- Update existing video messages to use default thumbnail
UPDATE message 
SET thumbnail_url = '/uploads/thumbnails/default_thumbnail.jpg' 
WHERE media_type = 'video' AND media_url IS NOT NULL AND thumbnail_url IS NULL; 