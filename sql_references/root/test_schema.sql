--
-- PostgreSQL database dump
--

-- Dumped from database version 16.8 (Homebrew)
-- Dumped by pg_dump version 16.8 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: create_default_engagement_settings(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.create_default_engagement_settings() RETURNS trigger
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


ALTER FUNCTION public.create_default_engagement_settings() OWNER TO postgres;

--
-- Name: create_default_member_preferences(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.create_default_member_preferences() RETURNS trigger
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


ALTER FUNCTION public.create_default_member_preferences() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.app_user (
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


ALTER TABLE public.app_user OWNER TO postgres;

--
-- Name: app_user_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.app_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.app_user_id_seq OWNER TO postgres;

--
-- Name: app_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.app_user_id_seq OWNED BY public.app_user.id;


--
-- Name: family; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.family (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by bigint
);


ALTER TABLE public.family OWNER TO postgres;

--
-- Name: family_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.family_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.family_id_seq OWNER TO postgres;

--
-- Name: family_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.family_id_seq OWNED BY public.family.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO postgres;

--
-- Name: invitation; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.invitation (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    token character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamp without time zone,
    sender_id bigint,
    family_id bigint,
    status character varying(50) DEFAULT 'PENDING'::character varying
);


ALTER TABLE public.invitation OWNER TO postgres;

--
-- Name: invitation_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.invitation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.invitation_id_seq OWNER TO postgres;

--
-- Name: invitation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.invitation_id_seq OWNED BY public.invitation.id;


--
-- Name: message; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.message (
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


ALTER TABLE public.message OWNER TO postgres;

--
-- Name: COLUMN message.family_id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.message.family_id IS 'The family this message belongs to';


--
-- Name: message_comment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.message_comment (
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


ALTER TABLE public.message_comment OWNER TO postgres;

--
-- Name: message_comment_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.message_comment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.message_comment_id_seq OWNER TO postgres;

--
-- Name: message_comment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.message_comment_id_seq OWNED BY public.message_comment.id;


--
-- Name: message_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.message_id_seq OWNER TO postgres;

--
-- Name: message_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.message_id_seq OWNED BY public.message.id;


--
-- Name: message_reaction; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.message_reaction (
    id bigint NOT NULL,
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    reaction_type character varying(20) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.message_reaction OWNER TO postgres;

--
-- Name: message_reaction_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.message_reaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.message_reaction_id_seq OWNER TO postgres;

--
-- Name: message_reaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.message_reaction_id_seq OWNED BY public.message_reaction.id;


--
-- Name: message_share; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.message_share (
    id bigint NOT NULL,
    original_message_id bigint NOT NULL,
    shared_by_user_id bigint NOT NULL,
    shared_to_family_id bigint NOT NULL,
    shared_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.message_share OWNER TO postgres;

--
-- Name: message_share_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.message_share_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.message_share_id_seq OWNER TO postgres;

--
-- Name: message_share_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.message_share_id_seq OWNED BY public.message_share.id;


--
-- Name: message_view; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.message_view (
    id bigint NOT NULL,
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    viewed_at timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.message_view OWNER TO postgres;

--
-- Name: message_view_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.message_view_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.message_view_id_seq OWNER TO postgres;

--
-- Name: message_view_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.message_view_id_seq OWNED BY public.message_view.id;


--
-- Name: user_engagement_settings; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_engagement_settings (
    user_id bigint NOT NULL,
    show_reactions_to_others boolean DEFAULT true NOT NULL,
    show_my_views_to_others boolean DEFAULT true NOT NULL,
    allow_sharing_my_messages boolean DEFAULT true NOT NULL,
    notify_on_reactions boolean DEFAULT true NOT NULL,
    notify_on_comments boolean DEFAULT true NOT NULL,
    notify_on_shares boolean DEFAULT true NOT NULL
);


ALTER TABLE public.user_engagement_settings OWNER TO postgres;

--
-- Name: user_family_membership; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_family_membership (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    family_id bigint NOT NULL,
    is_active boolean DEFAULT false,
    joined_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    role character varying(50) DEFAULT 'MEMBER'::character varying
);


ALTER TABLE public.user_family_membership OWNER TO postgres;

--
-- Name: TABLE user_family_membership; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.user_family_membership IS 'Stores the many-to-many relationship between users and families';


--
-- Name: user_family_membership_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.user_family_membership_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.user_family_membership_id_seq OWNER TO postgres;

--
-- Name: user_family_membership_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.user_family_membership_id_seq OWNED BY public.user_family_membership.id;


--
-- Name: user_family_message_settings; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_family_message_settings (
    user_id bigint NOT NULL,
    family_id bigint NOT NULL,
    receive_messages boolean DEFAULT true NOT NULL,
    last_updated timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.user_family_message_settings OWNER TO postgres;

--
-- Name: user_member_message_settings; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_member_message_settings (
    user_id bigint NOT NULL,
    family_id bigint NOT NULL,
    member_user_id bigint NOT NULL,
    receive_messages boolean DEFAULT true NOT NULL,
    last_updated timestamp without time zone
);


ALTER TABLE public.user_member_message_settings OWNER TO postgres;

--
-- Name: TABLE user_member_message_settings; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.user_member_message_settings IS 'Stores user preferences for receiving messages from specific family members';


--
-- Name: COLUMN user_member_message_settings.user_id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.user_member_message_settings.user_id IS 'The user ID of the preference owner';


--
-- Name: COLUMN user_member_message_settings.family_id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.user_member_message_settings.family_id IS 'The family ID for this preference';


--
-- Name: COLUMN user_member_message_settings.member_user_id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.user_member_message_settings.member_user_id IS 'The user ID of the family member this preference applies to';


--
-- Name: COLUMN user_member_message_settings.receive_messages; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.user_member_message_settings.receive_messages IS 'Whether to receive messages from this family member (true) or not (false)';


--
-- Name: COLUMN user_member_message_settings.last_updated; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.user_member_message_settings.last_updated IS 'When this preference was last updated';


--
-- Name: app_user id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user ALTER COLUMN id SET DEFAULT nextval('public.app_user_id_seq'::regclass);


--
-- Name: family id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.family ALTER COLUMN id SET DEFAULT nextval('public.family_id_seq'::regclass);


--
-- Name: invitation id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invitation ALTER COLUMN id SET DEFAULT nextval('public.invitation_id_seq'::regclass);


--
-- Name: message id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message ALTER COLUMN id SET DEFAULT nextval('public.message_id_seq'::regclass);


--
-- Name: message_comment id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_comment ALTER COLUMN id SET DEFAULT nextval('public.message_comment_id_seq'::regclass);


--
-- Name: message_reaction id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_reaction ALTER COLUMN id SET DEFAULT nextval('public.message_reaction_id_seq'::regclass);


--
-- Name: message_share id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_share ALTER COLUMN id SET DEFAULT nextval('public.message_share_id_seq'::regclass);


--
-- Name: message_view id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_view ALTER COLUMN id SET DEFAULT nextval('public.message_view_id_seq'::regclass);


--
-- Name: user_family_membership id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_membership ALTER COLUMN id SET DEFAULT nextval('public.user_family_membership_id_seq'::regclass);


--
-- Name: app_user app_user_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_email_key UNIQUE (email);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_username_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_username_key UNIQUE (username);


--
-- Name: family family_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.family
    ADD CONSTRAINT family_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: invitation invitation_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invitation
    ADD CONSTRAINT invitation_pkey PRIMARY KEY (id);


--
-- Name: invitation invitation_token_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invitation
    ADD CONSTRAINT invitation_token_key UNIQUE (token);


--
-- Name: message_comment message_comment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_comment
    ADD CONSTRAINT message_comment_pkey PRIMARY KEY (id);


--
-- Name: message message_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message
    ADD CONSTRAINT message_pkey PRIMARY KEY (id);


--
-- Name: message_reaction message_reaction_message_id_user_id_reaction_type_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_reaction
    ADD CONSTRAINT message_reaction_message_id_user_id_reaction_type_key UNIQUE (message_id, user_id, reaction_type);


--
-- Name: message_reaction message_reaction_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_reaction
    ADD CONSTRAINT message_reaction_pkey PRIMARY KEY (id);


--
-- Name: message_share message_share_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_share
    ADD CONSTRAINT message_share_pkey PRIMARY KEY (id);


--
-- Name: message_view message_view_message_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_view
    ADD CONSTRAINT message_view_message_id_user_id_key UNIQUE (message_id, user_id);


--
-- Name: message_view message_view_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_view
    ADD CONSTRAINT message_view_pkey PRIMARY KEY (id);


--
-- Name: family uk_family_creator; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.family
    ADD CONSTRAINT uk_family_creator UNIQUE (created_by);


--
-- Name: user_family_membership uk_user_family; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_membership
    ADD CONSTRAINT uk_user_family UNIQUE (user_id, family_id);


--
-- Name: user_engagement_settings user_engagement_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_engagement_settings
    ADD CONSTRAINT user_engagement_settings_pkey PRIMARY KEY (user_id);


--
-- Name: user_family_membership user_family_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_membership
    ADD CONSTRAINT user_family_membership_pkey PRIMARY KEY (id);


--
-- Name: user_family_message_settings user_family_message_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_message_settings
    ADD CONSTRAINT user_family_message_settings_pkey PRIMARY KEY (user_id, family_id);


--
-- Name: user_member_message_settings user_member_message_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_member_message_settings
    ADD CONSTRAINT user_member_message_settings_pkey PRIMARY KEY (user_id, family_id, member_user_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_app_user_country; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_app_user_country ON public.app_user USING btree (country);


--
-- Name: idx_app_user_email; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_app_user_email ON public.app_user USING btree (email);


--
-- Name: idx_app_user_state; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_app_user_state ON public.app_user USING btree (state);


--
-- Name: idx_app_user_username; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_app_user_username ON public.app_user USING btree (username);


--
-- Name: idx_invitation_email; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_invitation_email ON public.invitation USING btree (email);


--
-- Name: idx_invitation_family_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_invitation_family_id ON public.invitation USING btree (family_id);


--
-- Name: idx_invitation_sender_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_invitation_sender_id ON public.invitation USING btree (sender_id);


--
-- Name: idx_membership_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_membership_active ON public.user_family_membership USING btree (is_active);


--
-- Name: idx_membership_family; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_membership_family ON public.user_family_membership USING btree (family_id);


--
-- Name: idx_membership_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_membership_user ON public.user_family_membership USING btree (user_id);


--
-- Name: idx_message_comment_message_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_comment_message_id ON public.message_comment USING btree (message_id);


--
-- Name: idx_message_comment_parent_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_comment_parent_id ON public.message_comment USING btree (parent_comment_id);


--
-- Name: idx_message_comment_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_comment_user_id ON public.message_comment USING btree (user_id);


--
-- Name: idx_message_family; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_family ON public.message USING btree (family_id);


--
-- Name: idx_message_reaction_message_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_reaction_message_id ON public.message_reaction USING btree (message_id);


--
-- Name: idx_message_reaction_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_reaction_user_id ON public.message_reaction USING btree (user_id);


--
-- Name: idx_message_sender; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_sender ON public.message USING btree (sender_id);


--
-- Name: idx_message_settings_family; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_settings_family ON public.user_family_message_settings USING btree (family_id);


--
-- Name: idx_message_settings_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_settings_user ON public.user_family_message_settings USING btree (user_id);


--
-- Name: idx_message_share_original_message_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_share_original_message_id ON public.message_share USING btree (original_message_id);


--
-- Name: idx_message_share_shared_by_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_share_shared_by_user_id ON public.message_share USING btree (shared_by_user_id);


--
-- Name: idx_message_share_shared_to_family_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_share_shared_to_family_id ON public.message_share USING btree (shared_to_family_id);


--
-- Name: idx_message_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_user ON public.message USING btree (user_id);


--
-- Name: idx_message_view_message_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_view_message_id ON public.message_view USING btree (message_id);


--
-- Name: idx_message_view_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_view_user_id ON public.message_view USING btree (user_id);


--
-- Name: idx_user_member_message_settings_family_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_member_message_settings_family_id ON public.user_member_message_settings USING btree (family_id);


--
-- Name: idx_user_member_message_settings_member_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_member_message_settings_member_user_id ON public.user_member_message_settings USING btree (member_user_id);


--
-- Name: idx_user_member_message_settings_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_member_message_settings_user_id ON public.user_member_message_settings USING btree (user_id);


--
-- Name: app_user create_engagement_settings_for_new_user; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER create_engagement_settings_for_new_user AFTER INSERT ON public.app_user FOR EACH ROW EXECUTE FUNCTION public.create_default_engagement_settings();


--
-- Name: user_family_membership create_member_preferences_on_join; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER create_member_preferences_on_join AFTER INSERT ON public.user_family_membership FOR EACH ROW EXECUTE FUNCTION public.create_default_member_preferences();


--
-- Name: family fk_family_creator; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.family
    ADD CONSTRAINT fk_family_creator FOREIGN KEY (created_by) REFERENCES public.app_user(id);


--
-- Name: user_family_membership fk_membership_family; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_membership
    ADD CONSTRAINT fk_membership_family FOREIGN KEY (family_id) REFERENCES public.family(id);


--
-- Name: user_family_membership fk_membership_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_membership
    ADD CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: message fk_message_family; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message
    ADD CONSTRAINT fk_message_family FOREIGN KEY (family_id) REFERENCES public.family(id);


--
-- Name: message fk_message_sender; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message
    ADD CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES public.app_user(id);


--
-- Name: user_family_message_settings fk_message_settings_family; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_message_settings
    ADD CONSTRAINT fk_message_settings_family FOREIGN KEY (family_id) REFERENCES public.family(id);


--
-- Name: user_family_message_settings fk_message_settings_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_message_settings
    ADD CONSTRAINT fk_message_settings_user FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: message fk_message_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message
    ADD CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: invitation invitation_family_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invitation
    ADD CONSTRAINT invitation_family_id_fkey FOREIGN KEY (family_id) REFERENCES public.family(id);


--
-- Name: invitation invitation_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invitation
    ADD CONSTRAINT invitation_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.app_user(id);


--
-- Name: message_comment message_comment_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_comment
    ADD CONSTRAINT message_comment_message_id_fkey FOREIGN KEY (message_id) REFERENCES public.message(id) ON DELETE CASCADE;


--
-- Name: message_comment message_comment_parent_comment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_comment
    ADD CONSTRAINT message_comment_parent_comment_id_fkey FOREIGN KEY (parent_comment_id) REFERENCES public.message_comment(id) ON DELETE CASCADE;


--
-- Name: message_comment message_comment_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_comment
    ADD CONSTRAINT message_comment_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: message_reaction message_reaction_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_reaction
    ADD CONSTRAINT message_reaction_message_id_fkey FOREIGN KEY (message_id) REFERENCES public.message(id) ON DELETE CASCADE;


--
-- Name: message_reaction message_reaction_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_reaction
    ADD CONSTRAINT message_reaction_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: message_share message_share_original_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_share
    ADD CONSTRAINT message_share_original_message_id_fkey FOREIGN KEY (original_message_id) REFERENCES public.message(id) ON DELETE CASCADE;


--
-- Name: message_share message_share_shared_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_share
    ADD CONSTRAINT message_share_shared_by_user_id_fkey FOREIGN KEY (shared_by_user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: message_share message_share_shared_to_family_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_share
    ADD CONSTRAINT message_share_shared_to_family_id_fkey FOREIGN KEY (shared_to_family_id) REFERENCES public.family(id) ON DELETE CASCADE;


--
-- Name: message_view message_view_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_view
    ADD CONSTRAINT message_view_message_id_fkey FOREIGN KEY (message_id) REFERENCES public.message(id) ON DELETE CASCADE;


--
-- Name: message_view message_view_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message_view
    ADD CONSTRAINT message_view_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: user_engagement_settings user_engagement_settings_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_engagement_settings
    ADD CONSTRAINT user_engagement_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;


--
-- Name: user_member_message_settings user_member_message_settings_family_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_member_message_settings
    ADD CONSTRAINT user_member_message_settings_family_id_fkey FOREIGN KEY (family_id) REFERENCES public.family(id);


--
-- Name: user_member_message_settings user_member_message_settings_member_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_member_message_settings
    ADD CONSTRAINT user_member_message_settings_member_user_id_fkey FOREIGN KEY (member_user_id) REFERENCES public.app_user(id);


--
-- Name: user_member_message_settings user_member_message_settings_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_member_message_settings
    ADD CONSTRAINT user_member_message_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- PostgreSQL database dump complete
--

