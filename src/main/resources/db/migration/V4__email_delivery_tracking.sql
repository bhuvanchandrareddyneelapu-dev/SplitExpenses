-- V4: Add email delivery tracking and audit status to group_invitations
ALTER TABLE group_invitations ADD COLUMN IF NOT EXISTS email_delivery_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE group_invitations ADD COLUMN IF NOT EXISTS email_delivery_error TEXT;
ALTER TABLE group_invitations ADD COLUMN IF NOT EXISTS email_last_attempt_at TIMESTAMP;
