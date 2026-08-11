#!/usr/bin/env sh
set -eu

: "${1:?Usage: restore-postgres.sh /backups/file.dump}"
: "${DB_CONNECTION_IP:?DB_CONNECTION_IP is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

backup_file="$1"
DB_CONNECTION_PORT="${DB_CONNECTION_PORT:-5432}"

test -f "${backup_file}"
test -f "${backup_file}.sha256"
sha256sum -c "${backup_file}.sha256"

PGPASSWORD="${DB_PASSWORD}" pg_restore \
  --host="${DB_CONNECTION_IP}" \
  --port="${DB_CONNECTION_PORT}" \
  --username="${DB_USERNAME}" \
  --dbname="${DB_NAME}" \
  --clean \
  --if-exists \
  --no-owner \
  "${backup_file}"
