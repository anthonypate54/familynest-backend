-- Add status column to invitation table
ALTER TABLE invitation ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'PENDING'; 