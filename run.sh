#!/usr/bin/env bash
# Starts the Spring Boot backend and the Vite frontend together, waits until the
# frontend answers, then opens it in Google Chrome. Ctrl+C stops both servers.
#
# Usage: ./run.sh   (from any directory)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

BACKEND_PORT_START=8080   # first candidate; the first free port from here up is used
BACKEND_PORT_END=8099
FRONTEND_PORT=5173        # fixed: the backend's CORS allow-list expects this origin
FRONTEND_URL="http://localhost:$FRONTEND_PORT"
READY_TIMEOUT=120          # seconds to wait for the frontend to respond
BACKEND_READY_TIMEOUT=300  # the first Maven run compiles and can take minutes

PIDS=()

log() { printf '[run] %s\n' "$*"; }
die() { printf '[run] ERROR: %s\n' "$*" >&2; exit 1; }

# Stop each server's whole process group (Maven forks a JVM, npm runs Vite as a child).
cleanup() {
  trap - EXIT
  if (( ${#PIDS[@]} > 0 )); then
    log "Stopping servers..."
    for pid in "${PIDS[@]}"; do
      kill -- "-$pid" 2>/dev/null || true
    done
    # Wait only for the servers; a browser launched above may stay open and must not block us.
    wait "${PIDS[@]}" 2>/dev/null || true
    PIDS=()
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

# Prefix each line of a server's output so the two logs are easy to tell apart.
prefix_output() { sed -u "s/^/[$1] /"; }

open_in_chrome() {
  local url=$1
  if [[ "$(uname -s)" == "Darwin" ]]; then
    open -a "Google Chrome" "$url" && return 0
  else
    local candidate
    for candidate in google-chrome google-chrome-stable; do
      if command -v "$candidate" >/dev/null 2>&1; then
        nohup "$candidate" "$url" >/dev/null 2>&1 &
        return 0
      fi
    done
  fi
  log "WARNING: Google Chrome not found; open $url manually."
  return 1
}

# --- Preflight -------------------------------------------------------------

for tool in mvn npm curl; do
  command -v "$tool" >/dev/null 2>&1 || die "'$tool' is not on PATH."
done

if port_in_use "$FRONTEND_PORT"; then
  die "port $FRONTEND_PORT is already in use. Stop the process using it, then run again."
fi

BACKEND_PORT=""
for ((port = BACKEND_PORT_START; port <= BACKEND_PORT_END; port++)); do
  if ! port_in_use "$port"; then
    BACKEND_PORT=$port
    break
  fi
done
[[ -n "$BACKEND_PORT" ]] || die "no free backend port in $BACKEND_PORT_START-$BACKEND_PORT_END."
if (( BACKEND_PORT != BACKEND_PORT_START )); then
  log "Port $BACKEND_PORT_START is in use; using port $BACKEND_PORT for the backend."
fi

# Spring Boot reads SERVER_PORT; the Vite proxy reads BACKEND_URL. Both must agree.
export SERVER_PORT="$BACKEND_PORT"
export BACKEND_URL="http://localhost:$BACKEND_PORT"

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  log "frontend/node_modules not found; installing frontend dependencies..."
  (cd "$FRONTEND_DIR" && npm ci)
fi

# --- Start both servers ----------------------------------------------------

# Job control gives each background server its own process group, so cleanup can signal it.
set -m

log "Starting backend on $BACKEND_URL ..."
(cd "$BACKEND_DIR" && exec mvn spring-boot:run) \
  < /dev/null > >(prefix_output backend) 2>&1 &
BACKEND_PID=$!
PIDS+=("$BACKEND_PID")

log "Starting frontend on $FRONTEND_URL ..."
(cd "$FRONTEND_DIR" && exec npm run dev -- --strictPort) \
  < /dev/null > >(prefix_output frontend) 2>&1 &
FRONTEND_PID=$!
PIDS+=("$FRONTEND_PID")

# --- Wait for both servers, then open the browser --------------------------

# Succeeds once the URL returns any HTTP response (status 000 means nothing is listening yet).
wait_until_serving() {
  local name=$1 pid=$2 url=$3 timeout=$4 deadline
  log "Waiting for the $name to be ready (up to ${timeout}s)..."
  deadline=$((SECONDS + timeout))
  until [[ "$(curl -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null)" != "000" ]]; do
    kill -0 "$pid" 2>/dev/null || die "$name exited before it became ready."
    (( SECONDS < deadline )) || die "$name was not ready after ${timeout}s."
    sleep 1
  done
  log "$name is ready."
}

# The backend must be up before the browser loads the page, or the first message fails.
wait_until_serving "backend" "$BACKEND_PID" "$BACKEND_URL/" "$BACKEND_READY_TIMEOUT"
wait_until_serving "frontend" "$FRONTEND_PID" "$FRONTEND_URL" "$READY_TIMEOUT"

log "Opening $FRONTEND_URL in Google Chrome..."
open_in_chrome "$FRONTEND_URL" || true

log "Both servers are running. Press Ctrl+C to stop them."

# Keep running until either server exits; the EXIT trap then stops the other one.
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
  sleep 1
done
die "a server stopped unexpectedly; shutting down."
