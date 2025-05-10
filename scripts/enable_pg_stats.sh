#!/bin/bash
# Enable pg_stat_statements for PostgreSQL performance monitoring

echo "Enabling pg_stat_statements extension for performance monitoring..."

# Connect to postgres database first (this is always available)
PGPASSWORD=postgres psql -U postgres -d postgres << EOF
-- Enable the extension
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Update postgresql.conf to load the extension
DO \$\$
BEGIN
    -- Check if the configuration is already set
    IF NOT EXISTS (
        SELECT 1 FROM pg_settings 
        WHERE name = 'shared_preload_libraries' 
        AND setting LIKE '%pg_stat_statements%'
    ) THEN
        ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
        ALTER SYSTEM SET pg_stat_statements.track = 'all';
        ALTER SYSTEM SET pg_stat_statements.max = 10000;
    END IF;
END;
\$\$;
EOF

# Now connect to our specific database and create the extension there too
PGPASSWORD=postgres psql -U postgres -d familynest_test << EOF
-- Enable the extension in our database
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Reset the stats to start fresh
SELECT pg_stat_statements_reset();

-- Verify the extension is enabled
SELECT * FROM pg_extension WHERE extname = 'pg_stat_statements';
EOF

echo "pg_stat_statements extension enabled. PostgreSQL may need to be restarted for changes to take effect."
echo "To restart PostgreSQL: sudo service postgresql restart" 