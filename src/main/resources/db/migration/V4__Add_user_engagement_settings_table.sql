-- Add user_engagement_settings table
-- Add user_engagement_settings table
CREATE TABLE IF NOT EXISTS public.user_engagement_settings (
    user_id bigint NOT NULL PRIMARY KEY,
    show_reactions_to_others boolean DEFAULT true NOT NULL,
    show_my_views_to_others boolean DEFAULT true NOT NULL,
    allow_sharing_my_messages boolean DEFAULT true NOT NULL,
    notify_on_reactions boolean DEFAULT true NOT NULL,
    notify_on_comments boolean DEFAULT true NOT NULL,
    notify_on_shares boolean DEFAULT true NOT NULL,
    CONSTRAINT user_engagement_settings_user_id_fkey FOREIGN KEY (user_id) 
        REFERENCES public.app_user(id) ON DELETE CASCADE
);