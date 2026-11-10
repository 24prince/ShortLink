#!/usr/bin/env bash
# Quick manual demo against a running instance:  ./smoke-test.sh [base-url]
set -euo pipefail
BASE="${1:-http://localhost:8080}"

echo "1) Create a short link"
RESP=$(curl -s -X POST "$BASE/api/v1/urls" -H 'Content-Type: application/json' \
  -d '{"url":"https://github.com/spring-projects/spring-boot","expiresInDays":30}')
echo "$RESP"
CODE=$(echo "$RESP" | sed -E 's/.*"shortCode":"([^"]+)".*/\1/')

echo; echo "2) Follow it (expect 302 + Location header)"
curl -s -o /dev/null -D - "$BASE/$CODE" | grep -iE '^(HTTP|location)'

echo; echo "3) Stats"
curl -s "$BASE/api/v1/urls/$CODE/stats"

echo; echo "4) Hammer the create endpoint until the rate limiter kicks in"
for i in $(seq 1 25); do
  STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/urls" \
    -H 'Content-Type: application/json' -d "{\"url\":\"https://example.com/$i\"}")
  echo "request $i -> $STATUS"
done
