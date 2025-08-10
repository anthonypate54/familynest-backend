-- Populate user_notification_matrix from existing notification tables
-- This migration combines data from user_notification_settings, user_family_message_settings, and user_member_message_settings

-- Step 1: Create global settings for all users based on user_notification_settings
INSERT INTO user_notification_matrix (
    user_id, 
    family_id, 
    member_id,
    -- Push notification settings
    family_messages_push,
    dm_messages_push,
    invitations_push,
    reactions_push,
    comments_push,
    new_member_push,
    -- WebSocket settings (always enabled for in-app notifications)
    family_messages_websocket,
    dm_messages_websocket,
    invitations_websocket,
    reactions_websocket,
    comments_websocket,
    new_member_websocket,
    -- Device settings
    push_enabled,
    device_permission_granted,
    created_at,
    updated_at
)
SELECT 
    uns.user_id,
    0 as family_id,  -- Global settings
    0 as member_id,   -- Global settings
    -- Push settings from existing preferences
    uns.push_notifications_enabled as family_messages_push,
    uns.push_notifications_enabled as dm_messages_push,
    uns.invitation_notifications as invitations_push,
    uns.push_notifications_enabled as reactions_push,
    uns.push_notifications_enabled as comments_push,
    uns.new_member_notifications as new_member_push,
    -- WebSocket always enabled for in-app notifications
    TRUE as family_messages_websocket,
    TRUE as dm_messages_websocket,
    TRUE as invitations_websocket,
    TRUE as reactions_websocket,
    TRUE as comments_websocket,
    TRUE as new_member_websocket,
    -- Device settings
    uns.push_notifications_enabled as push_enabled,
    uns.device_permission_granted,
    uns.created_at,
    uns.updated_at
FROM user_notification_settings uns;

-- Step 2: Create family-specific overrides from user_family_message_settings
-- Only insert rows where receive_messages = FALSE (muted families)
INSERT INTO user_notification_matrix (
    user_id, 
    family_id, 
    member_id,
    -- Override family message settings only
    family_messages_push,
    dm_messages_push,
    invitations_push,
    reactions_push,
    comments_push,
    new_member_push,
    -- WebSocket settings (still enabled for UI updates)
    family_messages_websocket,
    dm_messages_websocket,
    invitations_websocket,
    reactions_websocket,
    comments_websocket,
    new_member_websocket,
    -- Device settings (inherit from global)
    push_enabled,
    device_permission_granted,
    created_at,
    updated_at
)
SELECT 
    ufms.user_id,
    ufms.family_id,
    0 as member_id,  -- Family-specific, not user-specific
    -- Override push settings for muted families
    ufms.receive_messages as family_messages_push,
    TRUE as dm_messages_push,  -- DMs not affected by family muting
    TRUE as invitations_push,  -- Invitations not affected by family muting
    ufms.receive_messages as reactions_push,
    ufms.receive_messages as comments_push,
    TRUE as new_member_push,   -- New member notifications not affected by family muting
    -- WebSocket always enabled
    TRUE as family_messages_websocket,
    TRUE as dm_messages_websocket,
    TRUE as invitations_websocket,
    TRUE as reactions_websocket,
    TRUE as comments_websocket,
    TRUE as new_member_websocket,
    -- Device settings (inherit from global)
    (SELECT push_notifications_enabled FROM user_notification_settings WHERE user_id = ufms.user_id) as push_enabled,
    (SELECT device_permission_granted FROM user_notification_settings WHERE user_id = ufms.user_id) as device_permission_granted,
    ufms.last_updated as created_at,
    ufms.last_updated as updated_at
FROM user_family_message_settings ufms
WHERE ufms.receive_messages = FALSE;  -- Only migrate muted families

-- Step 3: Create user-specific muting overrides from user_member_message_settings
-- Only insert rows where receive_messages = FALSE (muted users)
INSERT INTO user_notification_matrix (
    user_id, 
    family_id, 
    member_id,
    -- Override DM settings only
    family_messages_push,
    dm_messages_push,
    invitations_push,
    reactions_push,
    comments_push,
    new_member_push,
    -- WebSocket settings (still enabled for UI updates)
    family_messages_websocket,
    dm_messages_websocket,
    invitations_websocket,
    reactions_websocket,
    comments_websocket,
    new_member_websocket,
    -- Device settings (inherit from global)
    push_enabled,
    device_permission_granted,
    created_at,
    updated_at
)
SELECT 
    umms.user_id,
    0 as family_id,  -- Global user muting, not family-specific
    umms.member_user_id as member_id,
    -- Override DM settings for muted users
    TRUE as family_messages_push,  -- Family messages not affected by user muting
    umms.receive_messages as dm_messages_push,
    TRUE as invitations_push,      -- Invitations not affected by user muting
    TRUE as reactions_push,        -- Reactions not affected by user muting
    TRUE as comments_push,         -- Comments not affected by user muting
    TRUE as new_member_push,       -- New member notifications not affected by user muting
    -- WebSocket always enabled
    TRUE as family_messages_websocket,
    TRUE as dm_messages_websocket,
    TRUE as invitations_websocket,
    TRUE as reactions_websocket,
    TRUE as comments_websocket,
    TRUE as new_member_websocket,
    -- Device settings (inherit from global)
    (SELECT push_notifications_enabled FROM user_notification_settings WHERE user_id = umms.user_id) as push_enabled,
    (SELECT device_permission_granted FROM user_notification_settings WHERE user_id = umms.user_id) as device_permission_granted,
    umms.last_updated as created_at,
    umms.last_updated as updated_at
FROM user_member_message_settings umms
WHERE umms.receive_messages = FALSE;  -- Only migrate muted users

-- Add some helpful indexes that might not have been created in the previous migration
CREATE INDEX IF NOT EXISTS idx_notification_matrix_lookup 
    ON user_notification_matrix (user_id, family_id, member_id);

-- Add comments for the migrated data
COMMENT ON TABLE user_notification_matrix IS 'Unified notification preferences matrix - migrated from user_notification_settings, user_family_message_settings, and user_member_message_settings';