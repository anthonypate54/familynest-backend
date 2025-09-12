-- V44: Add session ID tracking for single device enforcement
-- This column stores a unique session ID per user to enforce single device login

-- Add session ID column to app_user table
ALTER TABLE app_user 
ADD COLUMN current_session_id VARCHAR(50);

-- Create index for efficient session ID lookups
CREATE INDEX idx_app_user_session_id ON app_user(current_session_id);

-- Add comment for clarity
COMMENT ON COLUMN app_user.current_session_id 
IS 'Unique session ID for single device enforcement - only one valid session per user';

-- Note: Session IDs are initially NULL and will be generated on login
-- When a user logs in on a new device, the old session ID becomes invalid
