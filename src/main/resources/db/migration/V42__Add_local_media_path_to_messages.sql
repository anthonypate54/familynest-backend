-- Add local_media_path column to message table for sender's instant video playback
ALTER TABLE message ADD COLUMN local_media_path VARCHAR(1000);

-- Add local_media_path column to dm_message table for sender's instant video playback  
ALTER TABLE dm_message ADD COLUMN local_media_path VARCHAR(1000);

-- Add comments to document the purpose
COMMENT ON COLUMN message.local_media_path IS 'Local file path on sender device for instant video playback, while media_url is server URL for others';
COMMENT ON COLUMN dm_message.local_media_path IS 'Local file path on sender device for instant video playback, while media_url is server URL for others';
