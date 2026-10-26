#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
base='sonatype/nexus3@sha256:772ad5badde07e15f2c25c750437fcbb01ebdb84a8c43a3ed38eda60bb6448f1'
image='mcmp/nexus-ui-fixed:3.97.0-20261021.3'
jar='sonatype-nexus-repository-3.97.0-13.jar'
mkdir -p build
container=$(docker create "$base")
trap 'docker rm "$container" >/dev/null' EXIT
docker cp "$container:/opt/sonatype/nexus/bin/$jar" "build/original-$jar"
python3 patch_nexus_ui.py "build/original-$jar" "build/$jar"
cp Dockerfile build/Dockerfile
docker build --network=none --build-arg "NEXUS_BASE=$base" -t "$image" build
