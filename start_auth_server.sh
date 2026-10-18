#!/bin/bash
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Start PostgreSQL service if not already started
brew services start postgresql@16

# A physical Android device has its own localhost. Forward the phone's port
# back to this computer so the app can reach the local auth API at localhost:3000.
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" && -f "$PROJECT_DIR/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$PROJECT_DIR/local.properties" | head -n 1 | sed 's/\\:/:/g')"
fi
ADB_BIN="$SDK_DIR/platform-tools/adb"
if [[ -x "$ADB_BIN" ]] && "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { found=1 } END { exit !found }'; then
  if "$ADB_BIN" reverse tcp:3000 tcp:3000; then
    echo "Forwarded Android localhost:3000 to this computer."
  else
    echo "Warning: could not configure adb reverse for port 3000."
  fi
else
  echo "No ADB-connected device found; Android localhost forwarding was skipped."
fi

# Start Auth Backend API
cd "$PROJECT_DIR/backend"
if curl -fsS --max-time 2 http://127.0.0.1:3000/api/health >/dev/null 2>&1; then
  echo "Auth API is already healthy on port 3000; keeping the existing server."
  exit 0
fi
echo "Starting Spotify PostgreSQL Auth Server on port 3000..."
node server.js
