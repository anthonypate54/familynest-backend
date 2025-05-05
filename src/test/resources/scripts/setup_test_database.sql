-- setup_test_database.sql
-- Script to create a clean test database and run all migrations

-- Drop and recreate the test database (this requires the user to have appropriate permissions)
-- NOTE: You'll need to run this section manually as a superuser
/*
DROP DATABASE IF EXISTS familynest_test;
CREATE DATABASE familynest_test WITH TEMPLATE template0;
GRANT ALL PRIVILEGES ON DATABASE familynest_test TO Anthony;
*/

-- Connect to the test database
-- \c familynest_test

-- Create the schema_history table for Flyway-like migration tracking
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INT NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INT,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT now(),
    execution_time INT NOT NULL,
    success BOOLEAN NOT NULL,
    PRIMARY KEY (installed_rank)
);

-- Create a function to execute migrations in order
CREATE OR REPLACE FUNCTION run_migrations() 
RETURNS VOID AS $$
DECLARE
    migration_file TEXT;
    migration_script TEXT;
    migration_version TEXT;
    migration_description TEXT;
    start_time TIMESTAMP;
    end_time TIMESTAMP;
    execution_time_ms INT;
    checksum_value INT;
    next_rank INT;
BEGIN
    -- Get the next rank to use
    SELECT COALESCE(MAX(installed_rank), 0) + 1 INTO next_rank FROM flyway_schema_history;
    
    -- This would be a loop through migration files in a real implementation
    -- For this demo, we'll manually include the important migration scripts
    
    -- Run V1__Initial_schema.sql
    migration_version := '1';
    migration_description := 'Initial schema';
    start_time := clock_timestamp();
    
    -- Execute the script from the original migration file
    -- In a real implementation, this would read from the file
    EXECUTE '
        -- Create tables for users, families, etc.
        -- (Paste the V1 migration script here)
    ';
    
    end_time := clock_timestamp();
    execution_time_ms := EXTRACT(EPOCH FROM (end_time - start_time)) * 1000;
    
    -- Record the migration
    INSERT INTO flyway_schema_history (
        installed_rank, version, description, type, script, 
        checksum, installed_by, installed_on, execution_time, success
    ) VALUES (
        next_rank, migration_version, migration_description, 'SQL', 'V1__Initial_schema.sql',
        NULL, current_user, now(), execution_time_ms, TRUE
    );
    
    next_rank := next_rank + 1;
    
    -- Run V25__Add_message_preferences.sql
    migration_version := '25';
    migration_description := 'Add message preferences';
    start_time := clock_timestamp();
    
    EXECUTE '
        -- Create message preferences tables
        CREATE TABLE IF NOT EXISTS user_family_message_settings (
            user_id BIGINT NOT NULL,
            family_id BIGINT NOT NULL,
            receive_messages BOOLEAN NOT NULL DEFAULT TRUE,
            last_updated TIMESTAMP,
            PRIMARY KEY (user_id, family_id)
        );
        
        -- Add foreign keys
        ALTER TABLE user_family_message_settings 
            ADD CONSTRAINT fk_message_settings_user FOREIGN KEY (user_id) REFERENCES app_user(id);
            
        ALTER TABLE user_family_message_settings 
            ADD CONSTRAINT fk_message_settings_family FOREIGN KEY (family_id) REFERENCES family(id);
            
        -- Create indexes
        CREATE INDEX idx_user_family_message_settings_user_id ON user_family_message_settings(user_id);
        CREATE INDEX idx_user_family_message_settings_family_id ON user_family_message_settings(family_id);
    ';
    
    end_time := clock_timestamp();
    execution_time_ms := EXTRACT(EPOCH FROM (end_time - start_time)) * 1000;
    
    -- Record the migration
    INSERT INTO flyway_schema_history (
        installed_rank, version, description, type, script, 
        checksum, installed_by, installed_on, execution_time, success
    ) VALUES (
        next_rank, migration_version, migration_description, 'SQL', 'V25__Add_message_preferences.sql',
        NULL, current_user, now(), execution_time_ms, TRUE
    );
    
    next_rank := next_rank + 1;
    
    -- Run V26__Add_member_message_preferences.sql
    migration_version := '26';
    migration_description := 'Add member message preferences';
    start_time := clock_timestamp();
    
    EXECUTE '
        -- Create member message preferences table
        CREATE TABLE IF NOT EXISTS user_member_message_settings (
            user_id BIGINT NOT NULL,
            family_id BIGINT NOT NULL,
            member_user_id BIGINT NOT NULL,
            receive_messages BOOLEAN NOT NULL DEFAULT TRUE,
            last_updated TIMESTAMP,
            PRIMARY KEY (user_id, family_id, member_user_id)
        );
        
        -- Add foreign keys
        ALTER TABLE user_member_message_settings 
            ADD CONSTRAINT user_member_message_settings_user_id_fkey 
            FOREIGN KEY (user_id) REFERENCES app_user(id);
            
        ALTER TABLE user_member_message_settings 
            ADD CONSTRAINT user_member_message_settings_family_id_fkey 
            FOREIGN KEY (family_id) REFERENCES family(id);
            
        ALTER TABLE user_member_message_settings 
            ADD CONSTRAINT user_member_message_settings_member_user_id_fkey 
            FOREIGN KEY (member_user_id) REFERENCES app_user(id);
            
        -- Create indexes
        CREATE INDEX idx_user_member_message_settings_user_id ON user_member_message_settings(user_id);
        CREATE INDEX idx_user_member_message_settings_family_id ON user_member_message_settings(family_id);
        CREATE INDEX idx_user_member_message_settings_member_user_id ON user_member_message_settings(member_user_id);
    ';
    
    end_time := clock_timestamp();
    execution_time_ms := EXTRACT(EPOCH FROM (end_time - start_time)) * 1000;
    
    -- Record the migration
    INSERT INTO flyway_schema_history (
        installed_rank, version, description, type, script, 
        checksum, installed_by, installed_on, execution_time, success
    ) VALUES (
        next_rank, migration_version, migration_description, 'SQL', 'V26__Add_member_message_preferences.sql',
        NULL, current_user, now(), execution_time_ms, TRUE
    );
    
    next_rank := next_rank + 1;
    
    -- Run V27__Add_message_preferences_trigger.sql
    migration_version := '27';
    migration_description := 'Add message preferences trigger';
    start_time := clock_timestamp();
    
    EXECUTE '
        -- Create function to populate member message preferences
        CREATE OR REPLACE FUNCTION create_default_member_preferences()
        RETURNS TRIGGER AS $$
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
        $$ LANGUAGE plpgsql;

        -- Create trigger
        DROP TRIGGER IF EXISTS create_member_preferences_on_join ON user_family_membership;
        CREATE TRIGGER create_member_preferences_on_join
        AFTER INSERT ON user_family_membership
        FOR EACH ROW
        EXECUTE FUNCTION create_default_member_preferences();

        -- Populate missing preferences for existing family memberships
        INSERT INTO user_member_message_settings (user_id, family_id, member_user_id, receive_messages)
        SELECT 
            user_memb.user_id, 
            user_memb.family_id, 
            family_memb.user_id,
            true
        FROM 
            user_family_membership user_memb
        CROSS JOIN 
            user_family_membership family_memb
        WHERE 
            user_memb.family_id = family_memb.family_id
            AND NOT EXISTS (
                SELECT 1 
                FROM user_member_message_settings 
                WHERE user_id = user_memb.user_id 
                AND family_id = user_memb.family_id 
                AND member_user_id = family_memb.user_id
            );
    ';
    
    end_time := clock_timestamp();
    execution_time_ms := EXTRACT(EPOCH FROM (end_time - start_time)) * 1000;
    
    -- Record the migration
    INSERT INTO flyway_schema_history (
        installed_rank, version, description, type, script, 
        checksum, installed_by, installed_on, execution_time, success
    ) VALUES (
        next_rank, migration_version, migration_description, 'SQL', 'V27__Add_message_preferences_trigger.sql',
        NULL, current_user, now(), execution_time_ms, TRUE
    );
    
    RAISE NOTICE 'Migration script execution completed';
END;
$$ LANGUAGE plpgsql;

-- Run the migrations
SELECT run_migrations();

-- Cleanup: Drop the migration function as it's no longer needed
DROP FUNCTION run_migrations();

-- Now the database should be fully set up with the schema
-- We can load the test data next 