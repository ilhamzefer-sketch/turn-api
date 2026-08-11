alter table registrations
    add column if not exists created_at timestamp default current_timestamp;

update registrations
set created_at = current_timestamp
where created_at is null;

alter table registrations
    alter column created_at set not null;
