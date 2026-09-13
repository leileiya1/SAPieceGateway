#!/usr/bin/env bash
# Run on the Linux Docker host after building sapiece-gateway:native.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target/native
container=$(docker create sapiece-gateway:native)
trap 'docker rm "$container" >/dev/null' EXIT
docker cp "$container:/app/." target/native/
chmod +x target/native/sapiece-gateway
file target/native/sapiece-gateway
# Portable Docker image archive: docker load < sapiece-gateway-native-image.tar.gz
docker save sapiece-gateway:native | gzip > target/native/sapiece-gateway-native-image.tar.gz
sha256sum target/native/sapiece-gateway target/native/*.so \
  target/native/sapiece-gateway-native-image.tar.gz > target/native/SHA256SUMS
