-- Create admin tables for admin interface
-- This migration creates the foundation for the admin dashboard

-- 1. ADMIN USERS TABLE
CREATE TABLE admin_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN', -- SUPER_ADMIN, ADMIN, SUPPORT
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_login TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. SYSTEM SETTINGS TABLE (for dynamic configuration)
CREATE TABLE system_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT NOT NULL,
    data_type VARCHAR(20) NOT NULL DEFAULT 'STRING', -- STRING, NUMBER, BOOLEAN, JSON
    description TEXT,
    updated_by BIGINT REFERENCES admin_users(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. USER NOTIFICATIONS TABLE (for announcements)
CREATE TABLE user_notifications (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    notification_type VARCHAR(50) NOT NULL, -- MAINTENANCE, ANNOUNCEMENT, PERSONAL, FEATURE, WARNING
    target_type VARCHAR(20) NOT NULL DEFAULT 'ALL', -- ALL, SPECIFIC_USER, TRIAL_USERS, PAID_USERS, ACTIVE_USERS
    target_user_id BIGINT REFERENCES app_user(id), -- NULL for broadcast messages
    is_active BOOLEAN NOT NULL DEFAULT true,
    show_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    show_until TIMESTAMP,
    priority INTEGER NOT NULL DEFAULT 1, -- 1=low, 2=medium, 3=high, 4=critical
    created_by BIGINT REFERENCES admin_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. USER NOTIFICATION READS TABLE (track who saw what)
CREATE TABLE user_notification_reads (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES user_notifications(id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(notification_id, user_id)
);

-- 5. ADMIN ACTIVITY LOG TABLE (audit trail)
CREATE TABLE admin_activity_log (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL REFERENCES admin_users(id),
    action VARCHAR(100) NOT NULL, -- USER_SEARCH, SUBSCRIPTION_UPDATE, SETTINGS_CHANGE, etc.
    target_type VARCHAR(50), -- USER, SETTING, NOTIFICATION
    target_id BIGINT, -- ID of affected record
    old_value TEXT, -- Previous state (JSON)
    new_value TEXT, -- New state (JSON)
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- INDEXES for performance
CREATE INDEX idx_user_notifications_active ON user_notifications(is_active, show_from, show_until);
CREATE INDEX idx_user_notifications_target ON user_notifications(target_type, target_user_id);
CREATE INDEX idx_user_notifications_type ON user_notifications(notification_type);
CREATE INDEX idx_notification_reads_user ON user_notification_reads(user_id);
CREATE INDEX idx_notification_reads_notification ON user_notification_reads(notification_id);
CREATE INDEX idx_admin_activity_log_admin ON admin_activity_log(admin_user_id, created_at);
CREATE INDEX idx_admin_activity_log_target ON admin_activity_log(target_type, target_id);
CREATE INDEX idx_system_settings_key ON system_settings(setting_key);

-- INITIAL DATA
-- Insert default system settings
INSERT INTO system_settings (setting_key, setting_value, data_type, description) VALUES
('subscription.monthly.price', '2.99', 'NUMBER', 'Monthly subscription price in USD'),
('maintenance.enabled', 'false', 'BOOLEAN', 'Global maintenance mode flag'),
('features.new_user_onboarding', 'true', 'BOOLEAN', 'Enable new user onboarding flow'),
('app.max_family_members', '20', 'NUMBER', 'Maximum members per family'),
('app.trial_days', '30', 'NUMBER', 'Default trial period in days'),
('app.support_email', 'support@infamilynest.com', 'STRING', 'Support contact email');

-- Create default admin user (password: admin123 - CHANGE THIS!)
-- Note: This password hash is for 'admin123' - should be changed immediately
INSERT INTO admin_users (username, email, password_hash, role) VALUES
('admin', 'admin@infamilynest.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye1J8NGFnr/nKHlqfQBBq7j12sV.9uiSG', 'SUPER_ADMIN');

-- Sample maintenance notification (inactive by default)
INSERT INTO user_notifications (title, message, notification_type, priority, is_active) VALUES
('Welcome to FamilyNest Admin', 
 'The admin system has been successfully installed. This is a sample notification that can be activated as needed.', 
 'ANNOUNCEMENT', 
 2,
 false);

-- Add comments for documentation
COMMENT ON TABLE admin_users IS 'Administrative users with access to admin dashboard';
COMMENT ON TABLE system_settings IS 'Dynamic system configuration settings manageable via admin interface';
COMMENT ON TABLE user_notifications IS 'Notifications and announcements that can be displayed to users';
COMMENT ON TABLE user_notification_reads IS 'Tracks which users have seen which notifications';
COMMENT ON TABLE admin_activity_log IS 'Audit log of all admin actions for security and compliance';

COMMENT ON COLUMN admin_users.role IS 'SUPER_ADMIN: full access, ADMIN: standard access, SUPPORT: read-only';
COMMENT ON COLUMN system_settings.data_type IS 'STRING, NUMBER, BOOLEAN, or JSON for complex settings';
COMMENT ON COLUMN user_notifications.target_type IS 'ALL, SPECIFIC_USER, TRIAL_USERS, PAID_USERS, or ACTIVE_USERS';
COMMENT ON COLUMN user_notifications.priority IS '1=low, 2=medium, 3=high, 4=critical';


