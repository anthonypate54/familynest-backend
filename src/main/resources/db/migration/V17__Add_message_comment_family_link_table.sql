-- V17: Add message_comment_family_link table for many-to-many relationship
-- Similar to message_family_link but for comments

-- Create the message_comment_family_link table
CREATE TABLE message_comment_family_link (
    id BIGSERIAL PRIMARY KEY,
    message_comment_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT fk_comment_family_link_comment 
        FOREIGN KEY (message_comment_id) REFERENCES message_comment(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_family_link_family 
        FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    
    -- Prevent duplicate entries
    CONSTRAINT uk_comment_family_link UNIQUE (message_comment_id, family_id)
);

-- Create indexes for performance
CREATE INDEX idx_comment_family_link_comment_id ON message_comment_family_link (message_comment_id);
CREATE INDEX idx_comment_family_link_family_id ON message_comment_family_link (family_id);
CREATE INDEX idx_comment_family_link_composite ON message_comment_family_link (message_comment_id, family_id);

-- Populate the link table with existing data from family_id column
-- For each comment, find all families that its parent message belongs to
INSERT INTO message_comment_family_link (message_comment_id, family_id)
SELECT DISTINCT 
    mc.id as message_comment_id,
    mfl.family_id
FROM message_comment mc
JOIN message_family_link mfl ON mc.parent_message_id = mfl.message_id
WHERE mc.family_id IS NOT NULL;

-- Verify the population worked correctly
DO $$
DECLARE
    comment_count INTEGER;
    link_count INTEGER;
    populated_count INTEGER;
BEGIN
    -- Count total comments
    SELECT COUNT(*) INTO comment_count FROM message_comment WHERE family_id IS NOT NULL;
    
    -- Count total links created
    SELECT COUNT(*) INTO link_count FROM message_comment_family_link;
    
    -- Count comments that have at least one link
    SELECT COUNT(DISTINCT message_comment_id) INTO populated_count FROM message_comment_family_link;
    
    RAISE NOTICE 'Migration V17 Summary:';
    RAISE NOTICE '- Total comments with family_id: %', comment_count;
    RAISE NOTICE '- Total comment-family links created: %', link_count;
    RAISE NOTICE '- Comments with at least one family link: %', populated_count;
    
    -- Show sample of multi-family comments (comments visible in multiple families)
    FOR i IN 1..LEAST(5, (SELECT COUNT(*) FROM (
        SELECT message_comment_id 
        FROM message_comment_family_link 
        GROUP BY message_comment_id 
        HAVING COUNT(*) > 1
    ) multi_family_comments)) LOOP
        DECLARE
            sample_comment_id BIGINT;
            family_list TEXT;
        BEGIN
            SELECT message_comment_id INTO sample_comment_id
            FROM message_comment_family_link 
            GROUP BY message_comment_id 
            HAVING COUNT(*) > 1 
            LIMIT 1 OFFSET (i-1);
            
            SELECT string_agg(family_id::TEXT, ', ') INTO family_list
            FROM message_comment_family_link 
            WHERE message_comment_id = sample_comment_id;
            
            RAISE NOTICE '- Comment % now visible in families: %', sample_comment_id, family_list;
        END;
    END LOOP;
END $$; 