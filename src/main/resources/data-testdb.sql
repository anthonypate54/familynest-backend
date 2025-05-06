-- FamilyNest Test Database Population Script
-- This file is automatically loaded by Spring Boot when using the testdb profile

-- First, load the base test data with users, families, etc.
\ir test-data-large.sql

-- Next, load the general message data (threads, views, reactions, comments)
\ir test-messages.sql

-- Finally, load the large message thread for load testing
\ir test-large-thread.sql

-- Print completion message
SELECT 'Test database populated successfully with users, families, messages, and a large thread for load testing' as status; 