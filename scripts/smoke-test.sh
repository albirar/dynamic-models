#!/usr/bin/env bash
# Smoke tests against a running instance of the API.
#
#   scripts/smoke-test.sh [BASE_URL]      (default: http://localhost:8080)
#
# Exits with a non-zero status if any check fails. Only needs bash and curl.
set -u

BASE_URL="${1:-http://localhost:8080}"
HEADER="X-Model-Sections"
FAILURES=0

# check <description> <expected status> <body pattern or ""> <curl args...>
check() {
    local description="$1" expected="$2" pattern="$3"
    shift 3
    local output status body
    output=$(curl -sS -m 10 -w '\n%{http_code}' "$@" 2>&1) || true
    status="${output##*$'\n'}"
    body="${output%$'\n'*}"
    if [[ "$status" != "$expected" ]]; then
        echo "FAIL  $description: HTTP $status (expected $expected)"
        FAILURES=$((FAILURES + 1))
    elif [[ -n "$pattern" ]] && ! grep -Eq -- "$pattern" <<<"$body"; then
        echo "FAIL  $description: body does not match /$pattern/"
        FAILURES=$((FAILURES + 1))
    else
        echo "OK    $description"
    fi
}

# check_absent <description> <expected status> <body pattern> <curl args...>
check_absent() {
    local description="$1" expected="$2" pattern="$3"
    shift 3
    local output status body
    output=$(curl -sS -m 10 -w '\n%{http_code}' "$@" 2>&1) || true
    status="${output##*$'\n'}"
    body="${output%$'\n'*}"
    if [[ "$status" != "$expected" ]]; then
        echo "FAIL  $description: HTTP $status (expected $expected)"
        FAILURES=$((FAILURES + 1))
    elif grep -Eq -- "$pattern" <<<"$body"; then
        echo "FAIL  $description: body matches /$pattern/"
        FAILURES=$((FAILURES + 1))
    else
        echo "OK    $description"
    fi
}

echo "Smoke tests against $BASE_URL"

check "application version"              200 '"name"'              "$BASE_URL/appVersion"
check "vehicles list"                    200 '"content":\[\{'      "$BASE_URL/vehicles?size=1"
check "brands list"                      200 '"content":\[\{'      "$BASE_URL/brands?size=1"
check "full model includes brand"        200 '"brand":'            "$BASE_URL/vehicles?size=1"
check_absent "empty selection omits brand"   200 '"brand":'            "$BASE_URL/vehicles?size=1&m="
check "selection by query parameter"     200 '"brand":'            "$BASE_URL/vehicles?size=1&m=B"
check "selection by header"              200 '"brand":'            -H "$HEADER: B" "$BASE_URL/vehicles?size=1"
check_absent "query parameter wins over header" 200 '"brand":'         -H "$HEADER: B" "$BASE_URL/vehicles?size=1&m="
check "invalid code is HTTP 400"         400 ''                    "$BASE_URL/vehicles?size=1&m=X"
check "API documentation"                200 '/vehicles'           "$BASE_URL/v3/api-docs"

if curl -sS -m 10 -o /dev/null -D - "$BASE_URL/vehicles?size=1" | grep -iq "^vary:.*$HEADER"; then
    echo "OK    Vary header contains $HEADER"
else
    echo "FAIL  Vary header does not contain $HEADER"
    FAILURES=$((FAILURES + 1))
fi

if [[ $FAILURES -gt 0 ]]; then
    echo "$FAILURES check(s) failed"
    exit 1
fi
echo "All checks passed"
