CREATE TABLE group_invitations (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT REFERENCES groups(id) ON DELETE CASCADE,
    sender_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    receiver_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL, -- PENDING, ACCEPTED, REJECTED, EXPIRED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    UNIQUE(group_id, receiver_id)
);

ALTER TABLE expenses ADD COLUMN verification_status VARCHAR(20) DEFAULT 'PENDING' NOT NULL;
ALTER TABLE expenses ADD COLUMN receipt_url VARCHAR(255);

CREATE TABLE expense_approvals (
    id BIGSERIAL PRIMARY KEY,
    expense_id BIGINT REFERENCES expenses(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL, -- PENDING, APPROVED, REJECTED, REQUESTED_PROOF
    comment VARCHAR(255),
    approved_at TIMESTAMP,
    UNIQUE(expense_id, user_id)
);
