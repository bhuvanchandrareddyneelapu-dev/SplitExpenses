-- V5: Add token_hash column and expand invitation_token length to 64
ALTER TABLE group_invitations ALTER COLUMN invitation_token TYPE VARCHAR(64);
ALTER TABLE group_invitations ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);
