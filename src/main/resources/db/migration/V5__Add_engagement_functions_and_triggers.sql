-- Function to create default engagement settings for new users
CREATE OR REPLACE FUNCTION public.create_default_engagement_settings()
RETURNS trigger
LANGUAGE plpgsql
AS $$
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
$$;

-- Function to create default member preferences
CREATE OR REPLACE FUNCTION public.create_default_member_preferences()
RETURNS trigger 
LANGUAGE plpgsql
AS $$
BEGIN
    -- For each member in the family, create a preference for the new member
    INSERT INTO user_member_message_settings (user_id, family_id, member_user_id, receive_messages)
    SELECT NEW.user_id, NEW.family_id, member.user_id, true
    FROM user_family_membership member
    WHERE member.family_id = NEW.family_id
    AND NOT EXISTS (
        SELECT 1 FROM user_member_message_settings
        WHERE user_id = NEW.user_id 
        AND family_id = NEW.family_id 
        AND member_user_id = member.user_id
    );
    
    RETURN NEW;
END;
$$;

-- Drop triggers if they exist
DROP TRIGGER IF EXISTS create_engagement_settings_for_new_user ON public.app_user;
DROP TRIGGER IF EXISTS create_member_preferences_on_join ON public.user_family_membership;

-- Create triggers
CREATE TRIGGER create_engagement_settings_for_new_user 
AFTER INSERT ON public.app_user 
FOR EACH ROW 
EXECUTE FUNCTION public.create_default_engagement_settings();

CREATE TRIGGER create_member_preferences_on_join 
AFTER INSERT ON public.user_family_membership 
FOR EACH ROW 
EXECUTE FUNCTION public.create_default_member_preferences();