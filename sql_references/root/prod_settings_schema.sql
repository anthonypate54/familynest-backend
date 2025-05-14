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
-- Name: idx_message_settings_family; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_settings_family ON public.user_family_message_settings USING btree (family_id);


--
-- Name: idx_message_settings_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_settings_user ON public.user_family_message_settings USING btree (user_id);


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

