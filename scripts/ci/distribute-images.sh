#!/usr/bin/env bash
set -euo pipefail

tag="${1:-}"
ubuntu_host="${2:-}"
[[ "$tag" =~ ^[0-9a-f]{7,40}$ ]] || { echo "Invalid immutable tag" >&2; exit 2; }
[[ "$ubuntu_host" =~ ^[A-Za-z0-9._-]+@[A-Za-z0-9._-]+$ ]] || {
  echo "Invalid Ubuntu SSH destination" >&2; exit 2;
}
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
archive="$repo_root/dist/sapiece-images-$tag.tar.gz"
checksum="$archive.sha256"
test -s "$archive"
(cd "$(dirname "$archive")" && sha256sum -c "$(basename "$checksum")")

# Docker builds; K3s runs the images with containerd. Both nodes import the
# exact same checksum-verified archive before any Deployment is changed.
gzip -dc "$archive" | sudo /usr/local/bin/k3s ctr images import -
ssh "$ubuntu_host" 'install -d -m 700 "$HOME/sapiece-ci-images"'
rsync -a --partial "$archive" "$checksum" "$ubuntu_host:~/sapiece-ci-images/"
ssh "$ubuntu_host" "cd ~/sapiece-ci-images && sha256sum -c '$(basename "$checksum")' && gzip -dc '$(basename "$archive")' | sudo /usr/local/bin/k3s ctr images import - && rm -f '$(basename "$archive")' '$(basename "$checksum")'"
