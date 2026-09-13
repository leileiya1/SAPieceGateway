#!/usr/bin/env bash
# Build and run a Linux native image on the deployment host using SSH keys.
set -euo pipefail
cd "$(dirname "$0")/.."
server="${1:-school-linux}"
compose_file="${2:-docker-compose.school-linux.yml}"
ssh_opts=(-o BatchMode=yes -o ConnectTimeout=10)
ssh "${ssh_opts[@]}" "$server" 'mkdir -p ~/sapiece-gateway-native'
rsync -az -e 'ssh -o BatchMode=yes -o ConnectTimeout=10' \
  --exclude .git --exclude .idea --exclude target --exclude logs \
  --include .env.example --exclude '.env' --exclude '.env.*' \
  --exclude '*.tar.gz' --exclude .DS_Store ./ "$server:sapiece-gateway-native/"
ssh "${ssh_opts[@]}" "$server" 'bash -se' -- "$compose_file" <<'REMOTE'
compose_file=$1
cd ~/sapiece-gateway-native
test -s .env || { echo 'Configure ~/sapiece-gateway-native/.env first; see NATIVE.md.' >&2; exit 1; }
docker compose -f "$compose_file" config --quiet
services=$(docker compose -f "$compose_file" config --services)
for service in mysql redis; do
  if printf '%s\n' "$services" | grep -qx "$service"; then
    docker compose -f "$compose_file" up -d "$service"
  fi
done
docker build --progress=plain -t sapiece-gateway:native .
docker compose -f "$compose_file" up -d gateway
for attempt in $(seq 1 40); do
  status=$(docker inspect --format '{{.State.Health.Status}}' sapiece-gateway-native)
  if [ "$status" = healthy ]; then
    echo 'Native gateway is healthy.'
    exit 0
  fi
  sleep 3
done
docker logs --tail 60 sapiece-gateway-native
exit 1
REMOTE
