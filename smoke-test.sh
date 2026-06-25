#!/usr/bin/env bash
set -euo pipefail

IMAGE="branch-test:smoke"
COMPOSE_FILE="docker-compose.smoke.yml"
BASE_URL="http://localhost:8080"
PASS=0
FAIL=0

cleanup() {
  echo
  echo "--- Tearing down..."
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans
}

check() {
  local label="$1" expected_status="$2" url="$3"
  local response http_status body

  response=$(curl -s -w "\n%{http_code}" "$url")
  http_status=$(tail -n1 <<< "$response")
  body=$(sed '$d' <<< "$response")

  if [[ "$http_status" -eq "$expected_status" ]]; then
    echo "PASS [$label] → $http_status"
    PASS=$((PASS + 1))
  else
    echo "FAIL [$label] → expected $expected_status got $http_status"
    echo "$body"
    FAIL=$((FAIL + 1))
  fi
}

wait_for_app() {
  local url="$1" retries=40 i
  echo "Waiting for app..."
  for ((i = 1; i <= retries; i++)); do
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$url" || true)
    if [[ "$code" != "000" ]]; then
      echo "App is up (HTTP $code)."
      return 0
    fi
    echo "  [$i/$retries] not ready yet..."
    sleep 3
  done
  echo "ERROR: app did not start within $((retries * 3))s."
  return 1
}

echo "=== Building Docker image ==="
docker build -t "$IMAGE" .

echo
echo "=== Starting services ==="
trap cleanup EXIT
docker compose -f "$COMPOSE_FILE" up -d

wait_for_app "$BASE_URL/users/octocat"

echo
echo "=== Smoke tests ==="
check "GET /users/octocat (known user)"                     200 "$BASE_URL/users/octocat"
check "GET /users/this-user-does-not-exist-xyzzy42 (404)"  404 "$BASE_URL/users/this-user-does-not-exist-xyzzy42"

echo
echo "Results: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]
