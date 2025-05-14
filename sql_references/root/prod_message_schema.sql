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

SET default_tablespace = '';

SET default_table_access_method = heap;

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
-- Name: message fk_message_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.message
    ADD CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES public.app_user(id);


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
-- PostgreSQL database dump complete
--

