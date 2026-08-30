alter table admin_accounts
    add column if not exists must_change_credentials boolean not null default false;

alter table admin_accounts
    add column if not exists credentials_changed_at timestamp;

update admin_accounts
set must_change_credentials = true
where username = 'admin';
