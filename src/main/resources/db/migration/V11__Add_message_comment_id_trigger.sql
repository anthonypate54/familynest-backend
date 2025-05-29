-- Create trigger to set id from sequence
CREATE OR REPLACE FUNCTION set_message_comment_id()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id IS NULL THEN
        NEW.id = nextval('message_comment_id_seq');
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER set_message_comment_id_trigger
    BEFORE INSERT ON message_comment
    FOR EACH ROW
    EXECUTE FUNCTION set_message_comment_id(); 