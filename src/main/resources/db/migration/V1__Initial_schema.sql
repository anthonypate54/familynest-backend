-- Create app_user table
CREATE TABLE app_user (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50),
    photo VARCHAR(255),
    family_id BIGINT
);

-- Create family table
CREATE TABLE family (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_by BIGINT,
    created_date TIMESTAMP
);

-- Create message table
CREATE TABLE message (
    id SERIAL PRIMARY KEY,
    content TEXT,
    created_date TIMESTAMP,
    user_id BIGINT,
    sender_id BIGINT,
    media_type VARCHAR(50),
    media_url VARCHAR(255)
);

-- Create invitation table
CREATE TABLE invitation (
    id SERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    family_id BIGINT NOT NULL,
    created_date TIMESTAMP
); 