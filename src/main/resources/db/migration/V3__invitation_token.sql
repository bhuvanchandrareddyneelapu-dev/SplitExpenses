-- V3: Add token-based invitation support
-- Adds secure UUID token, invitee email (for non-registered users),
-- expiry date and acceptance timestamp to group_invitations.

-- Make receiver_id nullable to support invitations to non-registered users
ALTER TABLE group_invitations ALTER COLUMN receiver_id DROP NOT NULL;

-- Drop the unique constraint on group_id and receiver_id as receiver_id is now nullable
-- and we allow multiple sequential invitations over time.
ALTER TABLE group_invitations DROP CONSTRAINT IF EXISTS group_invitations_group_id_receiver_id_key;

-- Add token column (UUID string, unique)
ALTER TABLE group_invitations ADD COLUMN invitation_token VARCHAR(36) UNIQUE;

-- Add invitee_email for tracking who was invited (works for both registered and new users)
ALTER TABLE group_invitations ADD COLUMN invitee_email VARCHAR(100);

-- Add expiry column (default 7 days from now when inserted)
ALTER TABLE group_invitations ADD COLUMN expires_at TIMESTAMP;

-- Add accepted_at column
ALTER TABLE group_invitations ADD COLUMN accepted_at TIMESTAMP;

-- Update existing rows: populate invitee_email from receiver, set token, set expiry
UPDATE group_invitations gi
SET
    invitee_email = (SELECT u.email FROM users u WHERE u.id = gi.receiver_id),
    invitation_token = SUBSTRING(REPLACE(CAST(RANDOM() AS VARCHAR) || CAST(RANDOM() AS VARCHAR) || CAST(RANDOM() AS VARCHAR) || CAST(RANDOM() AS VARCHAR), '.', ''), 1, 36),
    expires_at = CURRENT_TIMESTAMP + INTERVAL '7' DAY
WHERE gi.receiver_id IS NOT NULL;
