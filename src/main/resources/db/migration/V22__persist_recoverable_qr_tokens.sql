alter table qr_credentials add column if not exists public_token varchar(128);
alter table qr_credentials add column if not exists legacy_token_hash varchar(64);

create unique index if not exists uk_qr_credentials_public_token
    on qr_credentials (public_token);
create unique index if not exists uk_qr_credentials_legacy_token_hash
    on qr_credentials (legacy_token_hash);
