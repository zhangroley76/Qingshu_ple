#!/usr/bin/env bash
# Builds Qingjian's Android engine (libqingjian_android.so) from source with Qingshu's patches
# (third_party/qingjian/*.patch, e.g. offline association) into app/src/main/jniLibs/<abi>/.
#
# Usage: scripts/build-qingjian-engine.sh [abi ...]      (default: arm64-v8a)
# Needs: git, rustup (the Qingjian toolchain is installed automatically), cargo-ndk, and an NDK in
# ANDROID_NDK_HOME. Run scripts/fetch-qingjian-engine.sh --with-lm first for the dictionary and
# language model; this script only replaces the library.
set -euo pipefail

VERSION="${QINGJIAN_VERSION:-v0.3.2}"
ABIS=("$@")
[[ ${#ABIS[@]} -eq 0 ]] && ABIS=(arm64-v8a)

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${QINGJIAN_SRC:-$ROOT/build/qingjian-src}"
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to an Android NDK}"

if [[ ! -d "$SRC/.git" ]]; then
  git clone --depth 1 --branch "$VERSION" https://github.com/qingjian-team/qingjian.git "$SRC"
fi
cd "$SRC"
git reset -q --hard "$VERSION"
git clean -qfd
for patch in "$ROOT"/third_party/qingjian/*.patch; do
  echo "Applying $(basename "$patch")"
  git -c user.name=Qingshu -c user.email=noreply@github.com am -q "$patch"
done

declare -A TARGET=([arm64-v8a]=aarch64-linux-android [x86_64]=x86_64-linux-android)
args=()
for abi in "${ABIS[@]}"; do
  rustup target add "${TARGET[$abi]}"
  args+=(-t "$abi")
done
cargo ndk "${args[@]}" --platform 26 -o "$ROOT/app/src/main/jniLibs" \
  build -p qingjian-android --release --locked
ls -la "$ROOT"/app/src/main/jniLibs/*/libqingjian_android.so
