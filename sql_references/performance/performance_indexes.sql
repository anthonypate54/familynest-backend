-- Performance indexes for FamilyNest application
-- Run this script to add all needed indexes for optimal query performance

-- Message table indexes
CREATE INDEX IF NOT EXISTS idx_message_family_id ON message(family_id);
CREATE INDEX IF NOT EXISTS idx_message_sender_id ON message(sender_id);
CREATE INDEX IF NOT EXISTS idx_message_timestamp ON message(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_message_family_timestamp ON message(family_id, timestamp DESC);

-- User table indexes
CREATE INDEX IF NOT EXISTS idx_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_user_email ON app_user(email);

-- Family table indexes
CREATE INDEX IF NOT EXISTS idx_family_created_by ON family(created_by);
CREATE INDEX IF NOT EXISTS idx_family_name ON family(name);

-- Family membership indexes
CREATE INDEX IF NOT EXISTS idx_user_family_membership_user_id ON user_family_membership(user_id);
CREATE INDEX IF NOT EXISTS idx_user_family_membership_family_id ON user_family_membership(family_id);
CREATE INDEX IF NOT EXISTS idx_user_family_membership_user_family ON user_family_membership(user_id, family_id);

-- Message settings indexes
CREATE INDEX IF NOT EXISTS idx_user_family_message_settings_user_id ON user_family_message_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_family_message_settings_family_id ON user_family_message_settings(family_id);
CREATE INDEX IF NOT EXISTS idx_user_family_message_settings_user_family ON user_family_message_settings(user_id, family_id);

-- Member message settings indexes
CREATE INDEX IF NOT EXISTS idx_user_member_message_settings_user_id ON user_member_message_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_member_message_settings_family_id ON user_member_message_settings(family_id);
CREATE INDEX IF NOT EXISTS idx_user_member_message_settings_member_user_id ON user_member_message_settings(member_user_id);
CREATE INDEX IF NOT EXISTS idx_user_member_message_settings_composite ON user_member_message_settings(user_id, family_id, member_user_id);

-- Engagement table indexes
CREATE INDEX IF NOT EXISTS idx_message_view_message_id ON message_view(message_id);
CREATE INDEX IF NOT EXISTS idx_message_view_user_id ON message_view(user_id);
CREATE INDEX IF NOT EXISTS idx_message_view_message_user ON message_view(message_id, user_id);

CREATE INDEX IF NOT EXISTS idx_message_reaction_message_id ON message_reaction(message_id);
CREATE INDEX IF NOT EXISTS idx_message_reaction_user_id ON message_reaction(user_id);
CREATE INDEX IF NOT EXISTS idx_message_reaction_type ON message_reaction(reaction_type);
CREATE INDEX IF NOT EXISTS idx_message_reaction_message_user ON message_reaction(message_id, user_id);

CREATE INDEX IF NOT EXISTS idx_message_comment_message_id ON message_comment(message_id);
CREATE INDEX IF NOT EXISTS idx_message_comment_user_id ON message_comment(user_id);
CREATE INDEX IF NOT EXISTS idx_message_comment_parent_id ON message_comment(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_message_comment_created_at ON message_comment(created_at);

CREATE INDEX IF NOT EXISTS idx_message_share_original_message_id ON message_share(original_message_id);
CREATE INDEX IF NOT EXISTS idx_message_share_shared_by_user_id ON message_share(shared_by_user_id);
CREATE INDEX IF NOT EXISTS idx_message_share_shared_to_family_id ON message_share(shared_to_family_id);

-- Run analyze to update statistics for the query planner
ANALYZE message;
ANALYZE app_user;
ANALYZE family;
ANALYZE user_family_membership;
ANALYZE user_family_message_settings;
ANALYZE user_member_message_settings;
ANALYZE message_view;
ANALYZE message_reaction;
ANALYZE message_comment;
ANALYZE message_share; 