-- V6: Database Indexing for Group Invitations
-- Provides fast O(1) lookups for SHA-256 token hashes, UUID tokens,
-- duplicate invitation checks, and invitee pending status sweeps.

CREATE INDEX IF NOT EXISTS idx_group_invitations_token_hash ON group_invitations (token_hash);
CREATE INDEX IF NOT EXISTS idx_group_invitations_token ON group_invitations (invitation_token);
CREATE INDEX IF NOT EXISTS idx_group_invitations_group_email ON group_invitations (group_id, invitee_email);
CREATE INDEX IF NOT EXISTS idx_group_invitations_email_status ON group_invitations (invitee_email, status);
