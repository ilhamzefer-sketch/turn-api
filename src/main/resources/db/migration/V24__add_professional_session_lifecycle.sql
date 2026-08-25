alter table refresh_tokens
    add column if not exists last_activity_at timestamp;

alter table refresh_tokens
    add column if not exists idle_expires_at timestamp;

alter table refresh_tokens
    add column if not exists absolute_expires_at timestamp;

alter table refresh_tokens
    add column if not exists revoked_at timestamp;

alter table refresh_tokens
    add column if not exists revoke_reason varchar(50);

alter table refresh_tokens
    add column if not exists principal_version varchar(100);

update refresh_tokens
set last_activity_at = coalesce(last_used_at, created_at),
    idle_expires_at = expires_at,
    absolute_expires_at = expires_at
where last_activity_at is null
   or idle_expires_at is null
   or absolute_expires_at is null;

alter table refresh_tokens
    alter column last_activity_at set not null;

alter table refresh_tokens
    alter column idle_expires_at set not null;

alter table refresh_tokens
    alter column absolute_expires_at set not null;

create index if not exists idx_refresh_tokens_active_lifecycle
    on refresh_tokens (revoked, idle_expires_at, absolute_expires_at);
