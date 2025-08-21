-- Create user_message_read table for per-user comment read tracking
-- This replaces the global read_flag with per-user boolean tracking

CREATE TABLE user_message_read (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    message_id BIGINT NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    has_unread_comments BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, message_id)
);

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_message_read_user_id ON user_message_read(user_id);
CREATE INDEX IF NOT EXISTS idx_user_message_read_message_id ON user_message_read(message_id);
CREATE INDEX IF NOT EXISTS idx_user_message_read_unread ON user_message_read(has_unread_comments) WHERE has_unread_comments = true;

-- Add comments
COMMENT ON TABLE user_message_read IS 'Per-user tracking of unread comments on messages';
COMMENT ON COLUMN user_message_read.has_unread_comments IS 'true = user has unread comments (show red icon), false = user has read all comments';
