alter table payment_sessions
    add column if not exists authentication_issued_at timestamp;
