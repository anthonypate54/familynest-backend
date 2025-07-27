-- Fix message_id column to be nullable in message_view table
-- This is needed because DM messages will have dm_message_id instead of message_id

-- Make message_id nullable
ALTER TABLE message_view ALTER COLUMN message_id DROP NOT NULL; 