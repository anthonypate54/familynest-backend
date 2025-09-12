-- Add local_media_path column to message_comment table for sender's instant video playback
ALTER TABLE message_comment ADD COLUMN local_media_path VARCHAR(1000);

-- Add comment to document the purpose  
COMMENT ON COLUMN message_comment.local_media_path IS 'Local file path on sender device for instant video playback, while media_url is server URL for others';


