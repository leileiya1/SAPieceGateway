#!/usr/bin/env bash
set -euo pipefail

tag="${1:-}"
[[ "$tag" =~ ^[0-9a-f]{7,40}$ ]] || { echo "Invalid immutable tag: $tag" >&2; exit 2; }
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
dist="$repo_root/dist"
archive="$dist/sapiece-images-$tag.tar.gz"
cpus="${NATIVE_BUILD_CPUS:-12}"
[[ "$cpus" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid NATIVE_BUILD_CPUS" >&2; exit 2; }
mkdir -p "$dist"
retry() {
  local attempt
  for attempt in 1 2 3; do
    "$@" && return 0
    [[ "$attempt" == 3 ]] && return 1
    sleep $((attempt * 10))
  done
}

# The builder is also a K3s server and middleware node. Serialize native builds
# across Jenkins jobs and keep native-image below half of the host CPU count.
exec 9>/tmp/sapiece-native-build.lock
flock -w 7200 9
cd "$repo_root"
retry docker build --build-arg "NATIVE_BUILD_CPUS=$cpus" \
  -f Dockerfile.k3s -t "sapiece-gateway:$tag" .
retry docker build --target server \
  -t "sapiece-grpc-echo:$tag" examples/grpc-echo
retry docker build --target transcoder \
  -t "sapiece-grpc-transcoder:$tag" examples/grpc-echo
docker image inspect "sapiece-gateway:$tag" "sapiece-grpc-echo:$tag" \
  "sapiece-grpc-transcoder:$tag" > "$dist/image-metadata.json"
docker save "sapiece-gateway:$tag" "sapiece-grpc-echo:$tag" \
  "sapiece-grpc-transcoder:$tag" | gzip -1 > "$archive"
(cd "$dist" && sha256sum "$(basename "$archive")" > "$(basename "$archive").sha256")
