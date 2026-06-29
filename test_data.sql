-- SplitWiseMoney Test Data Script
-- Registers users Rahul, Priya, and Kiran and seeds initial trip group and transactions.

-- 1. Insert Users (Password: password123, hashed with BCrypt)
-- BCrypt hash for 'password123': $2a$10$wK1b8N1908pP91vN9U07OeS5d9bSg2H67w2kG22iXyO7.L79n5Hde
INSERT INTO users (id, full_name, email, password, created_at, updated_at) VALUES
(1, 'Rahul', 'rahul@test.com', '$2a$10$wK1b8N1908pP91vN9U07OeS5d9bSg2H67w2kG22iXyO7.L79n5Hde', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'Priya', 'priya@test.com', '$2a$10$wK1b8N1908pP91vN9U07OeS5d9bSg2H67w2kG22iXyO7.L79n5Hde', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'Kiran', 'kiran@test.com', '$2a$10$wK1b8N1908pP91vN9U07OeS5d9bSg2H67w2kG22iXyO7.L79n5Hde', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER SEQUENCE users_id_seq RESTART WITH 4;

-- 2. Insert Group "Goa Trip" created by Rahul
INSERT INTO groups (id, group_name, created_by, created_at) VALUES
(1, 'Goa Trip', 1, CURRENT_TIMESTAMP);

ALTER SEQUENCE groups_id_seq RESTART WITH 2;

-- 3. Add Members to Group
INSERT INTO group_members (id, group_id, user_id, joined_at) VALUES
(1, 1, 1, CURRENT_TIMESTAMP), -- Rahul
(2, 1, 2, CURRENT_TIMESTAMP), -- Priya
(3, 1, 3, CURRENT_TIMESTAMP); -- Kiran

ALTER SEQUENCE group_members_id_seq RESTART WITH 4;

-- 4. Rahul paid ₹1200 for Cab (split equally)
INSERT INTO expenses (id, group_id, paid_by, amount, description, category, expense_date, created_at) VALUES
(1, 1, 1, 1200.00, 'Cab', 'Travel', CURRENT_DATE, CURRENT_TIMESTAMP);

-- Shares: Rahul 400, Priya 400, Kiran 400
INSERT INTO expense_participants (id, expense_id, user_id, share_amount) VALUES
(1, 1, 1, 400.00),
(2, 1, 2, 400.00),
(3, 1, 3, 400.00);

ALTER SEQUENCE expenses_id_seq RESTART WITH 2;
ALTER SEQUENCE expense_participants_id_seq RESTART WITH 4;

-- 5. Priya paid ₹800 for Dinner (split equally)
INSERT INTO expenses (id, group_id, paid_by, amount, description, category, expense_date, created_at) VALUES
(2, 1, 2, 800.00, 'Dinner', 'Food', CURRENT_DATE, CURRENT_TIMESTAMP);

-- Shares: Rahul 266.67, Priya 266.67, Kiran 266.66
INSERT INTO expense_participants (id, expense_id, user_id, share_amount) VALUES
(4, 2, 1, 266.67),
(5, 2, 2, 266.67),
(6, 2, 3, 266.66);

ALTER SEQUENCE expenses_id_seq RESTART WITH 3;
ALTER SEQUENCE expense_participants_id_seq RESTART WITH 7;
