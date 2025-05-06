-- Script to create a dedicated PostgreSQL test database for UI testing
-- Run this script as a PostgreSQL superuser (postgres)
--
-- USAGE:
--   psql -U postgres -f scripts/create_test_db.sql
--
-- This script:
-- 1. Creates a database called 'familynest_test'
-- 2. Drops all existing tables if they exist
-- 3. Does NOT create any test data (use large-ui-dataset.sql for that)

-- Create database if it doesn't exist
-- Must be run from postgres user
DROP DATABASE IF EXISTS familynest_test;
CREATE DATABASE familynest_test;

-- Connect to the newly created database
\connect familynest_test

-- Make sure the database is empty to start with
DROP TABLE IF EXISTS user_member_message_settings CASCADE;
DROP TABLE IF EXISTS user_family_message_settings CASCADE;
DROP TABLE IF EXISTS message_view CASCADE;
DROP TABLE IF EXISTS message_reaction CASCADE;
DROP TABLE IF EXISTS message_share CASCADE;
DROP TABLE IF EXISTS message_comment CASCADE;
DROP TABLE IF EXISTS message CASCADE;
DROP TABLE IF EXISTS invitation CASCADE;
DROP TABLE IF EXISTS user_family_membership CASCADE;
DROP TABLE IF EXISTS user_engagement_settings CASCADE;
DROP TABLE IF EXISTS family CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;

-- Grant privileges to the user for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO familynesttest;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO familynesttest; 