#!/usr/bin/env bash
# Installs the Android SDK command-line tools and the SDK packages this project
# needs, then writes local.properties. Run it yourself: the SDK licenses are
# shown interactively and must be accepted by you.
#
# Usage: scripts/setup-sdk.sh            (installs to $ANDROID_HOME or ~/Android/Sdk)
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
CMDLINE_ZIP="commandlinetools-linux-16111833_latest.zip"
CMDLINE_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"
PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;36.1.0")

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
sdkmanager="$SDK/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$sdkmanager" ]; then
    echo "Installing Android command-line tools to $SDK"
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    curl -fL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_ZIP"
    echo "$CMDLINE_SHA1  $tmp/tools.zip" | sha1sum -c -
    unzip -q "$tmp/tools.zip" -d "$tmp"
    mkdir -p "$SDK/cmdline-tools"
    mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

echo "Review and accept the SDK licenses:"
"$sdkmanager" --sdk_root="$SDK" --licenses
"$sdkmanager" --sdk_root="$SDK" "${PACKAGES[@]}"

echo "sdk.dir=$SDK" > "$repo_root/local.properties"
echo "Done. Wrote $repo_root/local.properties (sdk.dir=$SDK)."
