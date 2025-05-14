-- Comprehensive schema for FamilyNest application
-- Recreated from actual production database dump

--
-- app_user table
--
CREATE TABLE app_user (
    id bigint NOT NULL,
    username character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    first_name character varying(255),
    last_name character varying(255),
    role character varying(50) NOT NULL,
    photo character varying(255),
    phone_number character varying(20),
    address character varying(255),
    city character varying(100),
    state character varying(100),
    zip_code character varying(20),
    country character varying(100),
    birth_date date,
    bio text,
    show_demographics boolean DEFAULT false
);

CREATE SEQUENCE app_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE app_user_id_seq OWNED BY app_user.id;
ALTER TABLE ONLY app_user ALTER COLUMN id SET DEFAULT nextval('app_user_id_seq'::regclass);

-- Constraints and indexes on app_user
ALTER TABLE ONLY app_user ADD CONSTRAINT app_user_email_key UNIQUE (email);
ALTER TABLE ONLY app_user ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);
ALTER TABLE ONLY app_user ADD CONSTRAINT app_user_username_key UNIQUE (username);

CREATE INDEX idx_app_user_country ON app_user USING btree (country);
CREATE INDEX idx_app_user_email ON app_user USING btree (email);
CREATE INDEX idx_app_user_state ON app_user USING btree (state);
CREATE INDEX idx_app_user_username ON app_user USING btree (username);

--
-- family table
--
CREATE TABLE family (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by bigint
);

CREATE SEQUENCE family_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE family_id_seq OWNED BY family.id;
ALTER TABLE ONLY family ALTER COLUMN id SET DEFAULT nextval('family_id_seq'::regclass);

-- Constraints on family
ALTER TABLE ONLY family ADD CONSTRAINT family_pkey PRIMARY KEY (id);
ALTER TABLE ONLY family ADD CONSTRAINT uk_family_creator UNIQUE (created_by);
ALTER TABLE ONLY family ADD CONSTRAINT fk_family_creator FOREIGN KEY (created_by) REFERENCES app_user(id);

--
-- user_family_membership table
--
CREATE TABLE user_family_membership (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    family_id bigint NOT NULL,
    is_active boolean DEFAULT false,
    joined_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    role character varying(50) DEFAULT 'MEMBER'::character varying
);

COMMENT ON TABLE user_family_membership IS 'Stores the many-to-many relationship between users and families';

CREATE SEQUENCE user_family_membership_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE user_family_membership_id_seq OWNED BY user_family_membership.id;
ALTER TABLE ONLY user_family_membership ALTER COLUMN id SET DEFAULT nextval('user_family_membership_id_seq'::regclass);

-- Constraints and indexes on user_family_membership
ALTER TABLE ONLY user_family_membership ADD CONSTRAINT uk_user_family UNIQUE (user_id, family_id);
ALTER TABLE ONLY user_family_membership ADD CONSTRAINT user_family_membership_pkey PRIMARY KEY (id);
ALTER TABLE ONLY user_family_membership ADD CONSTRAINT fk_membership_family FOREIGN KEY (family_id) REFERENCES family(id);
ALTER TABLE ONLY user_family_membership ADD CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_user(id);

CREATE INDEX idx_membership_active ON user_family_membership USING btree (is_active);
CREATE INDEX idx_membership_family ON user_family_membership USING btree (family_id);
CREATE INDEX idx_membership_user ON user_family_membership USING btree (user_id);

--
-- message table
--
CREATE TABLE message (
    id bigint NOT NULL,
    content text NOT NULL,
    "timestamp" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    user_id bigint NOT NULL,
    sender_id bigint,
    sender_username character varying(255),
    media_type text,
    media_url text,
    family_id bigint
);

COMMENT ON COLUMN message.family_id IS 'The family this message belongs to';

CREATE SEQUENCE message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE message_id_seq OWNED BY message.id;
ALTER TABLE ONLY message ALTER COLUMN id SET DEFAULT nextval('message_id_seq'::regclass);

-- Constraints and indexes on message
ALTER TABLE ONLY message ADD CONSTRAINT message_pkey PRIMARY KEY (id);
ALTER TABLE ONLY message ADD CONSTRAINT fk_message_family FOREIGN KEY (family_id) REFERENCES family(id);
ALTER TABLE ONLY message ADD CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES app_user(id);
ALTER TABLE ONLY message ADD CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES app_user(id);

CREATE INDEX idx_message_family ON message USING btree (family_id);
CREATE INDEX idx_message_sender ON message USING btree (sender_id);
CREATE INDEX idx_message_user ON message USING btree (user_id);

--
-- message_view table
--
CREATE TABLE message_view (
    id bigint NOT NULL,
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    viewed_at timestamp without time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE message_view_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE message_view_id_seq OWNED BY message_view.id;
ALTER TABLE ONLY message_view ALTER COLUMN id SET DEFAULT nextval('message_view_id_seq'::regclass);

-- Constraints and indexes on message_view
ALTER TABLE ONLY message_view ADD CONSTRAINT message_view_message_id_user_id_key UNIQUE (message_id, user_id);
ALTER TABLE ONLY message_view ADD CONSTRAINT message_view_pkey PRIMARY KEY (id);
ALTER TABLE ONLY message_view ADD CONSTRAINT message_view_message_id_fkey FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE;
ALTER TABLE ONLY message_view ADD CONSTRAINT message_view_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

CREATE INDEX idx_message_view_message_id ON message_view USING btree (message_id);
CREATE INDEX idx_message_view_user_id ON message_view USING btree (user_id);

--
-- message_reaction table
--
CREATE TABLE message_reaction (
    id bigint NOT NULL,
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    reaction_type character varying(20) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE message_reaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE message_reaction_id_seq OWNED BY message_reaction.id;
ALTER TABLE ONLY message_reaction ALTER COLUMN id SET DEFAULT nextval('message_reaction_id_seq'::regclass);

-- Constraints and indexes on message_reaction
ALTER TABLE ONLY message_reaction ADD CONSTRAINT message_reaction_message_id_user_id_reaction_type_key UNIQUE (message_id, user_id, reaction_type);
ALTER TABLE ONLY message_reaction ADD CONSTRAINT message_reaction_pkey PRIMARY KEY (id);
ALTER TABLE ONLY message_reaction ADD CONSTRAINT message_reaction_message_id_fkey FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE;
ALTER TABLE ONLY message_reaction ADD CONSTRAINT message_reaction_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

CREATE INDEX idx_message_reaction_message_id ON message_reaction USING btree (message_id);
CREATE INDEX idx_message_reaction_user_id ON message_reaction USING btree (user_id);

--
-- message_comment table
--
CREATE TABLE message_comment (
    id bigint NOT NULL,
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    content text NOT NULL,
    media_url character varying(255),
    media_type character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone,
    parent_comment_id bigint
);

CREATE SEQUENCE message_comment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE message_comment_id_seq OWNED BY message_comment.id;
ALTER TABLE ONLY message_comment ALTER COLUMN id SET DEFAULT nextval('message_comment_id_seq'::regclass);

-- Constraints and indexes on message_comment
ALTER TABLE ONLY message_comment ADD CONSTRAINT message_comment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY message_comment ADD CONSTRAINT message_comment_message_id_fkey FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE CASCADE;
ALTER TABLE ONLY message_comment ADD CONSTRAINT message_comment_parent_comment_id_fkey FOREIGN KEY (parent_comment_id) REFERENCES message_comment(id) ON DELETE CASCADE;
ALTER TABLE ONLY message_comment ADD CONSTRAINT message_comment_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

CREATE INDEX idx_message_comment_message_id ON message_comment USING btree (message_id);
CREATE INDEX idx_message_comment_parent_id ON message_comment USING btree (parent_comment_id);
CREATE INDEX idx_message_comment_user_id ON message_comment USING btree (user_id);

--
-- message_share table
--
CREATE TABLE message_share (
    id bigint NOT NULL,
    original_message_id bigint NOT NULL,
    shared_by_user_id bigint NOT NULL,
    shared_to_family_id bigint NOT NULL,
    shared_at timestamp without time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE message_share_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE message_share_id_seq OWNED BY message_share.id;
ALTER TABLE ONLY message_share ALTER COLUMN id SET DEFAULT nextval('message_share_id_seq'::regclass);

-- Constraints and indexes on message_share
ALTER TABLE ONLY message_share ADD CONSTRAINT message_share_pkey PRIMARY KEY (id);
ALTER TABLE ONLY message_share ADD CONSTRAINT message_share_original_message_id_fkey FOREIGN KEY (original_message_id) REFERENCES message(id) ON DELETE CASCADE;
ALTER TABLE ONLY message_share ADD CONSTRAINT message_share_shared_by_user_id_fkey FOREIGN KEY (shared_by_user_id) REFERENCES app_user(id) ON DELETE CASCADE;
ALTER TABLE ONLY message_share ADD CONSTRAINT message_share_shared_to_family_id_fkey FOREIGN KEY (shared_to_family_id) REFERENCES family(id) ON DELETE CASCADE;

CREATE INDEX idx_message_share_original_message_id ON message_share USING btree (original_message_id);
CREATE INDEX idx_message_share_shared_by_user_id ON message_share USING btree (shared_by_user_id);
CREATE INDEX idx_message_share_shared_to_family_id ON message_share USING btree (shared_to_family_id);

--
-- user_family_message_settings table
--
CREATE TABLE user_family_message_settings (
    user_id bigint NOT NULL,
    family_id bigint NOT NULL,
    receive_messages boolean DEFAULT true NOT NULL,
    last_updated timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

-- Constraints and indexes on user_family_message_settings
ALTER TABLE ONLY user_family_message_settings ADD CONSTRAINT user_family_message_settings_pkey PRIMARY KEY (user_id, family_id);
ALTER TABLE ONLY user_family_message_settings ADD CONSTRAINT fk_message_settings_family FOREIGN KEY (family_id) REFERENCES family(id);
ALTER TABLE ONLY user_family_message_settings ADD CONSTRAINT fk_message_settings_user FOREIGN KEY (user_id) REFERENCES app_user(id);

CREATE INDEX idx_message_settings_family ON user_family_message_settings USING btree (family_id);
CREATE INDEX idx_message_settings_user ON user_family_message_settings USING btree (user_id);

--
-- user_member_message_settings table
--
CREATE TABLE user_member_message_settings (
    user_id bigint NOT NULL,
    family_id bigint NOT NULL,
    member_user_id bigint NOT NULL,
    receive_messages boolean DEFAULT true NOT NULL,
    last_updated timestamp without time zone
);

COMMENT ON TABLE user_member_message_settings IS 'Stores user preferences for receiving messages from specific family members';
COMMENT ON COLUMN user_member_message_settings.user_id IS 'The user ID of the preference owner';
COMMENT ON COLUMN user_member_message_settings.family_id IS 'The family ID for this preference';
COMMENT ON COLUMN user_member_message_settings.member_user_id IS 'The user ID of the family member this preference applies to';
COMMENT ON COLUMN user_member_message_settings.receive_messages IS 'Whether to receive messages from this family member (true) or not (false)';
COMMENT ON COLUMN user_member_message_settings.last_updated IS 'When this preference was last updated';

-- Constraints and indexes on user_member_message_settings
ALTER TABLE ONLY user_member_message_settings ADD CONSTRAINT user_member_message_settings_pkey PRIMARY KEY (user_id, family_id, member_user_id);
ALTER TABLE ONLY user_member_message_settings ADD CONSTRAINT user_member_message_settings_family_id_fkey FOREIGN KEY (family_id) REFERENCES family(id);
ALTER TABLE ONLY user_member_message_settings ADD CONSTRAINT user_member_message_settings_member_user_id_fkey FOREIGN KEY (member_user_id) REFERENCES app_user(id);
ALTER TABLE ONLY user_member_message_settings ADD CONSTRAINT user_member_message_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_user(id);

CREATE INDEX idx_user_member_message_settings_family_id ON user_member_message_settings USING btree (family_id);
CREATE INDEX idx_user_member_message_settings_member_user_id ON user_member_message_settings USING btree (member_user_id);
CREATE INDEX idx_user_member_message_settings_user_id ON user_member_message_settings USING btree (user_id);

--
-- Add invitation table
--
CREATE TABLE invitation (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    token character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamp without time zone,
    sender_id bigint,
    family_id bigint
);

CREATE SEQUENCE invitation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE invitation_id_seq OWNED BY invitation.id;
ALTER TABLE ONLY invitation ALTER COLUMN id SET DEFAULT nextval('invitation_id_seq'::regclass);

-- Constraints on invitation
ALTER TABLE ONLY invitation ADD CONSTRAINT invitation_pkey PRIMARY KEY (id);
ALTER TABLE ONLY invitation ADD CONSTRAINT invitation_token_key UNIQUE (token);
ALTER TABLE ONLY invitation ADD CONSTRAINT invitation_family_id_fkey FOREIGN KEY (family_id) REFERENCES family(id);
ALTER TABLE ONLY invitation ADD CONSTRAINT invitation_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES app_user(id);

CREATE INDEX idx_invitation_email ON invitation USING btree (email);
CREATE INDEX idx_invitation_family_id ON invitation USING btree (family_id);
CREATE INDEX idx_invitation_sender_id ON invitation USING btree (sender_id); 