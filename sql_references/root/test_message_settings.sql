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
-- Name: user_family_message_settings user_family_message_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_family_message_settings
    ADD CONSTRAINT user_family_message_settings_pkey PRIMARY KEY (user_id, family_id);


--
-- Name: idx_message_settings_family; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_settings_family ON public.user_family_message_settings USING btree (family_id);


--
-- Name: idx_message_settings_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_message_settings_user ON public.user_family_message_settings USING btree (user_id);


--
-- Name: idx_user_family_message_settings_family_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_family_message_settings_family_id ON public.user_family_message_settings USING btree (family_id);


--
-- Name: idx_user_family_message_settings_user_family; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_family_message_settings_user_family ON public.user_family_message_settings USING btree (user_id, family_id);


--
-- Name: idx_user_family_message_settings_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_family_message_settings_user_id ON public.user_family_message_settings USING btree (user_id);


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
-- PostgreSQL database dump complete
--

