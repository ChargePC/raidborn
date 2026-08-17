#!/usr/bin/env sh
set -e
GRADLE_VERSION="8.8"
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
GRADLE_DIR="$APP_HOME/.gradle/wrapper/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_DIR/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  echo "Gradle $GRADLE_VERSION not found. Downloading into .gradle/wrapper..."
  mkdir -p "$APP_HOME/.gradle/wrapper"
  if command -v curl >/dev/null 2>&1; then
    curl -L -o "$APP_HOME/.gradle/wrapper/gradle-$GRADLE_VERSION-bin.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$APP_HOME/.gradle/wrapper/gradle-$GRADLE_VERSION-bin.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else
    echo "curl or wget is required to download Gradle."
    exit 1
  fi
  unzip -o "$APP_HOME/.gradle/wrapper/gradle-$GRADLE_VERSION-bin.zip" -d "$APP_HOME/.gradle/wrapper"
fi

exec "$GRADLE_BIN" "$@"
