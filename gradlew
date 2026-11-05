#!/usr/bin/env sh
set -eu
GRADLE_VERSION="8.9"
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/fpsplus-bootstrap/gradle-${GRADLE_VERSION}"
GRADLE_BIN="$CACHE_DIR/gradle-${GRADLE_VERSION}/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$CACHE_DIR"
  ZIP="$CACHE_DIR/gradle-${GRADLE_VERSION}-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "FPS+: Install Gradle 8.9 or provide curl/wget to bootstrap it." >&2
    exit 1
  fi
  unzip -q -o "$ZIP" -d "$CACHE_DIR"
  rm -f "$ZIP"
fi
exec "$GRADLE_BIN" -p "$BASE_DIR" "$@"
