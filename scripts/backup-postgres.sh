#!/usr/bin/env sh
set -eu

: "${DB_CONNECTION_IP:?DB_CONNECTION_IP is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

DB_CONNECTION_PORT="${DB_CONNECTION_PORT:-5432}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${BACKUP_DIR}/${DB_NAME}-${timestamp}.dump"

mkdir -p "${BACKUP_DIR}"
PGPASSWORD="${DB_PASSWORD}" pg_dump \
  --host="${DB_CONNECTION_IP}" \
  --port="${DB_CONNECTION_PORT}" \
  --username="${DB_USERNAME}" \
  --dbname="${DB_NAME}" \
  --format=custom \
  --no-owner \
  --file="${backup_file}"

pg_restore --list "${backup_file}" >/dev/null
sha256sum "${backup_file}" > "${backup_file}.sha256"
find "${BACKUP_DIR}" -type f \( -name '*.dump' -o -name '*.dump.sha256' \) -mtime "+${BACKUP_RETENTION_DAYS}" -delete
printf 'Backup verified: %s\n' "${backup_file}"
