-- Create unified notification preferences matrix table
-- This replaces the complex multi-table notification logic with a single fast lookup table

CREATE TABLE user_notification_matrix (
    user_id BIGINT NOT NULL,
    
    -- Context fields (0 = global setting)
    family_id BIGINT NOT NULL DEFAULT 0,  -- 0 = global, >0 = family-specific overrides
    member_id BIGINT NOT NULL DEFAULT 0,  -- 0 = global, >0 = user-specific muting
    
    -- Notification types for PUSH delivery
    family_messages_push BOOLEAN NOT NULL DEFAULT TRUE,
    dm_messages_push BOOLEAN NOT NULL DEFAULT TRUE,
    invitations_push BOOLEAN NOT NULL DEFAULT TRUE,
    reactions_push BOOLEAN NOT NULL DEFAULT TRUE,
    comments_push BOOLEAN NOT NULL DEFAULT TRUE,
    new_member_push BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Notification types for WEBSOCKET delivery
    family_messages_websocket BOOLEAN NOT NULL DEFAULT TRUE,
    dm_messages_websocket BOOLEAN NOT NULL DEFAULT TRUE,
    invitations_websocket BOOLEAN NOT NULL DEFAULT TRUE,
    reactions_websocket BOOLEAN NOT NULL DEFAULT TRUE,
    comments_websocket BOOLEAN NOT NULL DEFAULT TRUE,
    new_member_websocket BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Device/system settings
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    device_permission_granted BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Primary key ensures one row per user+context combination
    PRIMARY KEY (user_id, family_id, member_id),
    
    -- Foreign key constraints (only for user_id, family_id and member_id can be 0)
    CONSTRAINT fk_notification_matrix_user 
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Create optimized indexes for fast notification lookups
CREATE INDEX idx_notification_matrix_user_global 
    ON user_notification_matrix (user_id) 
    WHERE family_id = 0 AND member_id = 0;

CREATE INDEX idx_notification_matrix_user_family 
    ON user_notification_matrix (user_id, family_id) 
    WHERE family_id > 0 AND member_id = 0;

CREATE INDEX idx_notification_matrix_user_member 
    ON user_notification_matrix (user_id, member_id) 
    WHERE family_id = 0 AND member_id > 0;

-- Index for efficient push notification queries
CREATE INDEX idx_notification_matrix_push_enabled 
    ON user_notification_matrix (push_enabled, device_permission_granted) 
    WHERE push_enabled = TRUE AND device_permission_granted = TRUE;

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_notification_matrix_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_notification_matrix_updated_at
    BEFORE UPDATE ON user_notification_matrix
    FOR EACH ROW
    EXECUTE FUNCTION update_notification_matrix_updated_at();

-- Add helpful comments
COMMENT ON TABLE user_notification_matrix IS 'Unified notification preferences matrix - replaces complex multi-table notification logic';
COMMENT ON COLUMN user_notification_matrix.family_id IS '0 = global setting, >0 = family-specific override';
COMMENT ON COLUMN user_notification_matrix.member_id IS '0 = global setting, >0 = user-specific muting override';