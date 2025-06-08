-- Clean conversation link table
CREATE TABLE dm_conversation (
  id BIGSERIAL PRIMARY KEY,
  user1_id BIGINT NOT NULL,        -- Always the lower user_id
  user2_id BIGINT NOT NULL,        -- Always the higher user_id  
  family_context_id BIGINT,        -- Optional: which family brought them together
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  CONSTRAINT unique_conversation UNIQUE (user1_id, user2_id),
  CONSTRAINT fk_dm_conv_user1 FOREIGN KEY (user1_id) REFERENCES app_user(id),
  CONSTRAINT fk_dm_conv_user2 FOREIGN KEY (user2_id) REFERENCES app_user(id),
  CONSTRAINT fk_dm_conv_family FOREIGN KEY (family_context_id) REFERENCES family(id)
);

-- Indexes for dm_conversation
CREATE INDEX idx_dm_conv_user1 ON dm_conversation(user1_id);
CREATE INDEX idx_dm_conv_user2 ON dm_conversation(user2_id);
CREATE INDEX idx_dm_conv_family_context ON dm_conversation(family_context_id);

-- DM messages table
CREATE TABLE dm_message (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  content TEXT,
  
  -- Media fields
  media_url VARCHAR(500),              
  media_type VARCHAR(50),              -- 'photo', 'video', 'file', null
  media_thumbnail VARCHAR(500),        
  media_filename VARCHAR(255),         
  media_size BIGINT,                   
  media_duration INTEGER,              -- For videos (seconds)
  
  -- DM-specific fields
  is_read BOOLEAN DEFAULT FALSE,       
  delivered_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  CONSTRAINT fk_dm_msg_conversation FOREIGN KEY (conversation_id) REFERENCES dm_conversation(id),
  CONSTRAINT fk_dm_msg_sender FOREIGN KEY (sender_id) REFERENCES app_user(id)
);

-- Indexes for dm_message
CREATE INDEX idx_dm_msg_conversation_created ON dm_message(conversation_id, created_at);
CREATE INDEX idx_dm_msg_sender ON dm_message(sender_id);
CREATE INDEX idx_dm_msg_unread ON dm_message(conversation_id, is_read);