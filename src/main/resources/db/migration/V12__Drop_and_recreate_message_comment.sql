-- Drop existing table and sequence
DROP TABLE IF EXISTS message_comment CASCADE;
DROP SEQUENCE IF EXISTS message_comment_id_seq;

-- Create sequence
CREATE SEQUENCE message_comment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Create table
CREATE TABLE message_comment (
    id bigint DEFAULT nextval('message_comment_id_seq'::regclass) NOT NULL,
    content text NOT NULL,
    "timestamp" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    user_id bigint NOT NULL,
    sender_id bigint,
    sender_username character varying(255),
    media_type text,
    media_url text,
    family_id bigint,
    thumbnail_url text,
    parent_message_id bigint
);

-- Add comments
COMMENT ON TABLE message_comment IS 'Stores comments/replies to messages, allowing for nested discussions';
COMMENT ON COLUMN message_comment.id IS 'Primary key';
COMMENT ON COLUMN message_comment.content IS 'Text content of the comment';
COMMENT ON COLUMN message_comment."timestamp" IS 'When the comment was created';
COMMENT ON COLUMN message_comment.user_id IS 'ID of the user who created the comment';
COMMENT ON COLUMN message_comment.sender_id IS 'ID of the user who sent the comment';
COMMENT ON COLUMN message_comment.sender_username IS 'Username of the sender';
COMMENT ON COLUMN message_comment.media_type IS 'Type of media (photo, video)';
COMMENT ON COLUMN message_comment.media_url IS 'URL to any media attached to the comment';
COMMENT ON COLUMN message_comment.family_id IS 'The family this comment belongs to';
COMMENT ON COLUMN message_comment.thumbnail_url IS 'URL path to video thumbnail image';
COMMENT ON COLUMN message_comment.parent_message_id IS 'References the parent message this comment belongs to. If NULL, this is a regular comment.';

-- Add constraints
ALTER TABLE ONLY message_comment ADD CONSTRAINT message_comment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY message_comment ADD CONSTRAINT fk_message_comment_family FOREIGN KEY (family_id) REFERENCES family(id);
ALTER TABLE ONLY message_comment ADD CONSTRAINT fk_message_comment_parent FOREIGN KEY (parent_message_id) REFERENCES message(id) ON DELETE CASCADE;
ALTER TABLE ONLY message_comment ADD CONSTRAINT fk_message_comment_sender FOREIGN KEY (sender_id) REFERENCES app_user(id);
ALTER TABLE ONLY message_comment ADD CONSTRAINT fk_message_comment_user FOREIGN KEY (user_id) REFERENCES app_user(id);

-- Create indexes
CREATE INDEX idx_message_comment_family ON message_comment USING btree (family_id);
CREATE INDEX idx_message_comment_parent ON message_comment USING btree (parent_message_id);
CREATE INDEX idx_message_comment_sender ON message_comment USING btree (sender_id);
CREATE INDEX idx_message_comment_thumbnail_url ON message_comment USING btree (thumbnail_url);
CREATE INDEX idx_message_comment_user ON message_comment USING btree (user_id); 