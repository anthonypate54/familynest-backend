-- Reset the message_id_seq to be one more than the maximum id in the message table
SELECT setval('message_id_seq', COALESCE((SELECT MAX(id) FROM message), 0) + 1, false); 