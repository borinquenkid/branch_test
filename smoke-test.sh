#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0

check() {
  local label="$1" expected_status="$2" url="$3"
  local response http_status body

  response=$(curl -s -w "\n%{http_code}" "$url")
  http_status=$(tail -n1 <<< "$response")
  body=$(head -n -1 <<< "$response")

  if [[ "$http_status" -eq "$expected_status" ]]; then
    echo "PASS [$label] $url → $http_status"
    PASS=$((PASS + 1))
    echo "$body"
  else
    echo "FAIL [$label] $url → expected $expected_status got $http_status"
    FAIL=$((FAIL + 1))
    echo "$body"
  fi
  echo
}

echo "Smoke test against $BASE_URL"
echo "---"

check "known user"       200 "$BASE_URL/users/octocat"
check "user not found"   404 "$BASE_URL/users/this-user-does-not-exist-xyzzy42"

echo "---"
echo "Results: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]
