-- Drop existing table if it exists
DROP TABLE IF EXISTS message_comment;

-- Create sequence for message_comment id
CREATE SEQUENCE IF NOT EXISTS message_comment_id_seq;

-- Create new message_comment table
CREATE TABLE message_comment (
    id BIGINT PRIMARY KEY DEFAULT nextval('message_comment_id_seq'),
    content TEXT,
    media_url VARCHAR(255),
    media_type VARCHAR(50),
    thumbnail_url VARCHAR(255),
    video_url VARCHAR(255),
    sender_id BIGINT NOT NULL,
    sender_user_name VARCHAR(255),
    sender_photo VARCHAR(255),
    parent_message_id BIGINT NOT NULL,
    parent_comment_id BIGINT,
    family_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metrics JSONB,
    FOREIGN KEY (sender_id) REFERENCES app_user(id),
    FOREIGN KEY (parent_message_id) REFERENCES message(id),
    FOREIGN KEY (parent_comment_id) REFERENCES message_comment(id),
    FOREIGN KEY (family_id) REFERENCES family(id)
);

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_message_comment_updated_at
    BEFORE UPDATE ON message_comment
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add indexes for better performance
CREATE INDEX idx_message_comment_sender ON message_comment(sender_id);
CREATE INDEX idx_message_comment_parent ON message_comment(parent_message_id);
CREATE INDEX idx_message_comment_parent_comment ON message_comment(parent_comment_id);
CREATE INDEX idx_message_comment_family ON message_comment(family_id);
CREATE INDEX idx_message_comment_created_at ON message_comment(created_at);

-- Add comments to explain the table and its relationships
COMMENT ON TABLE message_comment IS 'Stores comments/replies to messages, allowing for nested discussions';
COMMENT ON COLUMN message_comment.id IS 'Primary key';
COMMENT ON COLUMN message_comment.content IS 'Text content of the comment';
COMMENT ON COLUMN message_comment.media_url IS 'URL to any media attached to the comment';
COMMENT ON COLUMN message_comment.media_type IS 'Type of media (photo, video)';
COMMENT ON COLUMN message_comment.thumbnail_url IS 'URL to video thumbnail if media is a video';
COMMENT ON COLUMN message_comment.video_url IS 'URL to video if media is a video';
COMMENT ON COLUMN message_comment.sender_id IS 'ID of the user who sent the comment';
COMMENT ON COLUMN message_comment.sender_user_name IS 'Username of the sender';
COMMENT ON COLUMN message_comment.sender_photo IS 'Profile photo URL of the sender';
COMMENT ON COLUMN message_comment.parent_message_id IS 'ID of the message this comment is replying to';
COMMENT ON COLUMN message_comment.parent_comment_id IS 'ID of the parent comment if this is a reply to another comment';
COMMENT ON COLUMN message_comment.family_id IS 'ID of the family this comment belongs to';
COMMENT ON COLUMN message_comment.created_at IS 'Timestamp when the comment was created';
COMMENT ON COLUMN message_comment.updated_at IS 'Timestamp when the comment was last updated';
COMMENT ON COLUMN message_comment.metrics IS 'JSON object storing metrics like likes, loves, and comment counts'; 