#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
state="$repo_root/dist/previous-images.txt"
test -s "$state" || exit 0
kubectl=(sudo /usr/local/bin/k3s kubectl -n sapiece)
while IFS='=' read -r deployment image; do
  case "$deployment" in
    sapiece-gateway) container=gateway ;;
    grpc-echo) container=grpc-echo ;;
    grpc-transcoder) container=envoy ;;
    *) echo "Refusing unknown deployment: $deployment" >&2; exit 2 ;;
  esac
  [[ "$image" =~ ^sapiece-[a-z-]+:[A-Za-z0-9._-]+$ ]] || {
    echo "Refusing invalid rollback image: $image" >&2; exit 2;
  }
  "${kubectl[@]}" set image "deployment/$deployment" "$container=$image"
done < "$state"
"${kubectl[@]}" rollout status deployment/sapiece-gateway --timeout=360s
"${kubectl[@]}" rollout status deployment/grpc-echo --timeout=240s
"${kubectl[@]}" rollout status deployment/grpc-transcoder --timeout=240s
