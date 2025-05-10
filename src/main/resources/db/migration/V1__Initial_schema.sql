-- Create app_user table with BIGINT IDs to match Hibernate entity
CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    photo VARCHAR(255),
    phone_number VARCHAR(20),
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    zip_code VARCHAR(20),
    country VARCHAR(100),
    birth_date DATE,
    bio TEXT,
    show_demographics BOOLEAN DEFAULT FALSE
);

-- Create family table
CREATE TABLE IF NOT EXISTS family (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT
);

-- Create message table
CREATE TABLE IF NOT EXISTS message (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL,
    sender_id BIGINT,
    sender_username VARCHAR(255),
    media_type TEXT,
    media_url TEXT
);

-- Create invitation table
CREATE TABLE IF NOT EXISTS invitation (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    sender_id BIGINT,
    family_id BIGINT
);

-- Add foreign key constraints
-- Removing the family_id constraint since that column doesn't exist in app_user
-- ALTER TABLE app_user 
--    ADD CONSTRAINT fk_user_family 
--    FOREIGN KEY (family_id) 
--    REFERENCES family(id);

ALTER TABLE family 
    ADD CONSTRAINT fk_family_creator 
    FOREIGN KEY (created_by) 
    REFERENCES app_user(id);

ALTER TABLE message 
    ADD CONSTRAINT fk_message_user 
    FOREIGN KEY (user_id) 
    REFERENCES app_user(id);

ALTER TABLE message 
    ADD CONSTRAINT fk_message_sender 
    FOREIGN KEY (sender_id) 
    REFERENCES app_user(id);

ALTER TABLE invitation 
    ADD CONSTRAINT fk_invitation_sender 
    FOREIGN KEY (sender_id) 
    REFERENCES app_user(id);

ALTER TABLE invitation 
    ADD CONSTRAINT fk_invitation_family 
    FOREIGN KEY (family_id) 
    REFERENCES family(id);

-- Create indexes for better search performance
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);
CREATE INDEX IF NOT EXISTS idx_app_user_state ON app_user(state);
CREATE INDEX IF NOT EXISTS idx_app_user_country ON app_user(country);
CREATE INDEX IF NOT EXISTS idx_message_user ON message(user_id);
CREATE INDEX IF NOT EXISTS idx_message_sender ON message(sender_id);
CREATE INDEX IF NOT EXISTS idx_invitation_email ON invitation(email);
CREATE INDEX IF NOT EXISTS idx_invitation_token ON invitation(token);
CREATE INDEX IF NOT EXISTS idx_invitation_family ON invitation(family_id); 