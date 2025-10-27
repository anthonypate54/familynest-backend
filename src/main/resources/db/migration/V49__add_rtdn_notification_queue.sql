-- Create table for queueing RTDN notifications
CREATE TABLE rtdn_notification_queue (
  id SERIAL PRIMARY KEY,
  purchase_token VARCHAR(255) NOT NULL,
  notification_data JSONB NOT NULL,
  notification_type INT NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT NOW(),
  processed BOOLEAN NOT NULL DEFAULT FALSE,
  processed_at TIMESTAMP
);

-- Add indexes for faster lookups
CREATE INDEX idx_rtdn_queue_purchase_token ON rtdn_notification_queue(purchase_token);
CREATE INDEX idx_rtdn_queue_processed ON rtdn_notification_queue(processed);
