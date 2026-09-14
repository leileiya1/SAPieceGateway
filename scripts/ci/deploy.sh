#!/usr/bin/env bash
set -euo pipefail

tag="${1:-}"
[[ "$tag" =~ ^[0-9a-f]{7,40}$ ]] || { echo "Invalid immutable tag" >&2; exit 2; }
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
dist="$repo_root/dist"
mkdir -p "$dist"
kubectl=(sudo /usr/local/bin/k3s kubectl)
rendered_manifest="$(mktemp)"
trap 'rm -f "$rendered_manifest"' EXIT
{
  "${kubectl[@]}" -n sapiece get deployment sapiece-gateway -o jsonpath='sapiece-gateway={.spec.template.spec.containers[0].image}{"\n"}'
  "${kubectl[@]}" -n sapiece get deployment grpc-echo -o jsonpath='grpc-echo={.spec.template.spec.containers[0].image}{"\n"}'
  "${kubectl[@]}" -n sapiece get deployment grpc-transcoder -o jsonpath='grpc-transcoder={.spec.template.spec.containers[0].image}{"\n"}'
} > "$dist/previous-images.txt"

"${kubectl[@]}" kustomize "$repo_root/k8s" > "$rendered_manifest"
sed -E -i \
  -e "s#image: sapiece-gateway:[^[:space:]]+#image: sapiece-gateway:$tag#" \
  -e "s#image: sapiece-grpc-echo:[^[:space:]]+#image: sapiece-grpc-echo:$tag#" \
  -e "s#image: sapiece-grpc-transcoder:[^[:space:]]+#image: sapiece-grpc-transcoder:$tag#" \
  "$rendered_manifest"
grep -q "image: sapiece-gateway:$tag" "$rendered_manifest"
grep -q "image: sapiece-grpc-echo:$tag" "$rendered_manifest"
grep -q "image: sapiece-grpc-transcoder:$tag" "$rendered_manifest"
# Validate with the API server using the same client-side apply mode as the real
# update. Existing resources were bootstrapped in this mode, so this also checks
# their current field ownership and immutable fields without mutating them.
"${kubectl[@]}" apply --dry-run=server -f "$rendered_manifest" >/dev/null
"${kubectl[@]}" apply -f "$rendered_manifest"
"${kubectl[@]}" -n sapiece rollout status deployment/sapiece-gateway --timeout=360s
"${kubectl[@]}" -n sapiece rollout status deployment/grpc-echo --timeout=240s
"${kubectl[@]}" -n sapiece rollout status deployment/grpc-transcoder --timeout=240s
"${kubectl[@]}" -n observability rollout status deployment/prometheus --timeout=240s
"${kubectl[@]}" -n sapiece get deployment sapiece-gateway grpc-echo grpc-transcoder \
  -o jsonpath='{range .items[*]}{.metadata.name}{"="}{.spec.template.spec.containers[0].image}{"\n"}{end}' \
  > "$dist/deployed-images.txt"
