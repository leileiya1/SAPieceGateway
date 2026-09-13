#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo $0 [env-file] [mysql-dump]" >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-$repo_root/.env}"
mysql_dump="${2:-}"
kubectl=(k3s kubectl)

if [[ ! -f "$env_file" ]]; then
  echo "Missing environment file: $env_file" >&2
  exit 1
fi

read_value() {
  sed -n "s/^$1=//p" "$env_file" | tail -n 1
}

required=(DB_USERNAME DB_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD JWT_SECRET GATEWAY_DOWNSTREAM_SECRET OAUTH_ENCRYPTION_KEY OAUTH_ALLOWED_HOSTS)
passthrough=("${required[@]}" CORS_ALLOWED_ORIGINS SIGNATURE_SECRET)
for key in "${required[@]}"; do
  if [[ -z "$(read_value "$key")" ]]; then
    echo "Missing required value $key in $env_file" >&2
    exit 1
  fi
done

secret_env="$(mktemp)"
trap 'rm -f "$secret_env"' EXIT
chmod 600 "$secret_env"
{
  printf 'DB_URL=%s\n' 'r2dbc:mysql://mysql.sapiece.svc.cluster.local:3306/sapiece_gateway?useSSL=false&serverTimezone=Asia/Shanghai'
  printf 'REDIS_HOST=%s\n' 'redis.sapiece.svc.cluster.local'
  for key in "${passthrough[@]}"; do
    printf '%s=%s\n' "$key" "$(read_value "$key")"
  done
} > "$secret_env"

"${kubectl[@]}" apply -f "$repo_root/k8s/namespace.yaml"
"${kubectl[@]}" -n sapiece create secret generic sapiece-gateway-secret \
  --from-env-file="$secret_env" --dry-run=client -o yaml | "${kubectl[@]}" apply -f -
"${kubectl[@]}" -n sapiece create configmap sapiece-gateway-schema \
  --from-file=01-schema.sql="$repo_root/sql/schema-native.sql" --dry-run=client -o yaml | \
  "${kubectl[@]}" apply -f -

node_name="$(hostname | tr '[:upper:]' '[:lower:]')"
"${kubectl[@]}" label node "$node_name" sapiece.io/data=true --overwrite
"${kubectl[@]}" apply -f "$repo_root/k8s/middleware.yaml"
"${kubectl[@]}" -n sapiece rollout status statefulset/mysql --timeout=240s
"${kubectl[@]}" -n sapiece rollout status statefulset/redis --timeout=180s

if [[ -n "$mysql_dump" ]]; then
  if [[ ! -f "$mysql_dump" ]]; then
    echo "MySQL dump does not exist: $mysql_dump" >&2
    exit 1
  fi
  "${kubectl[@]}" -n sapiece exec -i statefulset/mysql -- sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot sapiece_gateway' < "$mysql_dump"
fi

if ! "${kubectl[@]}" -n sapiece get secret grpc-echo-server-tls grpc-transcoder-client-tls >/dev/null 2>&1; then
  "$repo_root/scripts/k3s/rotate-grpc-mtls.sh"
fi
"$repo_root/scripts/k3s/deploy-stack.sh"
"${kubectl[@]}" -n sapiece get pods,svc,pvc -o wide
