#!/bin/sh
set -eu

db_secret=${DB_PASSWORD_FILE:-/run/kb-secrets/current/db_password}
jwt_secret=${JWT_SECRET_KEY_FILE:-/run/kb-secrets/current/jwt_secret}
for secret in "$db_secret" "$jwt_secret"; do
  if [ ! -f "$secret" ] || [ -L "$secret" ] || [ ! -r "$secret" ]; then
    printf '%s\n' 'Missing readable generated runtime secrets; inspect runtime-init logs.' >&2
    exit 1
  fi
done

# File values are authoritative, including when a legacy .env contains weak defaults.
DB_PASSWORD=$(cat "$db_secret")
JWT_SECRET_KEY=$(cat "$jwt_secret")
if [ "${#DB_PASSWORD}" -lt 64 ] || [ "${#JWT_SECRET_KEY}" -lt 96 ]; then
  printf '%s\n' 'Generated runtime secrets are incomplete; refusing to start.' >&2
  exit 1
fi
export DB_PASSWORD JWT_SECRET_KEY
unset DB_PASSWORD_FILE JWT_SECRET_KEY_FILE db_secret jwt_secret
exec java -jar /app/application.jar --server.address=0.0.0.0 --server.port=8080
