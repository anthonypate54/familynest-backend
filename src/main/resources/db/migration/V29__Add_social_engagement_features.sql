-- Migration: V29__Add_social_engagement_features.sql
-- Description: Adds database tables for social engagement features including
-- reactions, comments, views, shares, and user engagement settings

-- Message Reactions Table
CREATE TABLE message_reaction (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reaction_type VARCHAR(20) NOT NULL, -- "LIKE", "LOVE", "LAUGH", etc.
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    UNIQUE (message_id, user_id, reaction_type) -- One reaction type per user per message
);

CREATE INDEX idx_message_reaction_message_id ON message_reaction(message_id);
CREATE INDEX idx_message_reaction_user_id ON message_reaction(user_id);

-- Message Comments Table
CREATE TABLE message_comment (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    media_url VARCHAR(255),
    media_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    parent_comment_id BIGINT, -- For threaded comments (replies to comments)
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id) REFERENCES message_comment(id) ON DELETE CASCADE
);

CREATE INDEX idx_message_comment_message_id ON message_comment(message_id);
CREATE INDEX idx_message_comment_user_id ON message_comment(user_id);
CREATE INDEX idx_message_comment_parent_id ON message_comment(parent_comment_id);

-- Message Views Table
CREATE TABLE message_view (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    viewed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    UNIQUE (message_id, user_id) -- Each user can view a message only once (for counting)
);

CREATE INDEX idx_message_view_message_id ON message_view(message_id);
CREATE INDEX idx_message_view_user_id ON message_view(user_id);

-- Message Shares Table
CREATE TABLE message_share (
    id BIGSERIAL PRIMARY KEY,
    original_message_id BIGINT NOT NULL,
    shared_by_user_id BIGINT NOT NULL,
    shared_to_family_id BIGINT NOT NULL,
    shared_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (original_message_id) REFERENCES message(id) ON DELETE CASCADE,
    FOREIGN KEY (shared_by_user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    FOREIGN KEY (shared_to_family_id) REFERENCES family(id) ON DELETE CASCADE
);

CREATE INDEX idx_message_share_original_message_id ON message_share(original_message_id);
CREATE INDEX idx_message_share_shared_by_user_id ON message_share(shared_by_user_id);
CREATE INDEX idx_message_share_shared_to_family_id ON message_share(shared_to_family_id);

-- User Engagement Settings Table
CREATE TABLE user_engagement_settings (
    user_id BIGINT PRIMARY KEY,
    show_reactions_to_others BOOLEAN NOT NULL DEFAULT TRUE,
    show_my_views_to_others BOOLEAN NOT NULL DEFAULT TRUE,
    allow_sharing_my_messages BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_reactions BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_comments BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_shares BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Create function to automatically create default engagement settings for new users
CREATE OR REPLACE FUNCTION create_default_engagement_settings()
RETURNS TRIGGER AS $$
BEGIN
    -- Create default engagement settings for the new user
    INSERT INTO user_engagement_settings (
        user_id, 
        show_reactions_to_others, 
        show_my_views_to_others, 
        allow_sharing_my_messages,
        notify_on_reactions,
        notify_on_comments,
        notify_on_shares
    ) VALUES (
        NEW.id, 
        TRUE, 
        TRUE, 
        TRUE,
        TRUE,
        TRUE,
        TRUE
    );
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to add default engagement settings when a new user is created
CREATE TRIGGER create_engagement_settings_for_new_user
AFTER INSERT ON app_user
FOR EACH ROW
EXECUTE FUNCTION create_default_engagement_settings();

-- Add default settings for existing users
INSERT INTO user_engagement_settings (
    user_id, 
    show_reactions_to_others, 
    show_my_views_to_others, 
    allow_sharing_my_messages,
    notify_on_reactions,
    notify_on_comments,
    notify_on_shares
)
SELECT 
    id, 
    TRUE, 
    TRUE, 
    TRUE,
    TRUE,
    TRUE,
    TRUE
FROM app_user
WHERE NOT EXISTS (
    SELECT 1 FROM user_engagement_settings 
    WHERE user_engagement_settings.user_id = app_user.id
); 