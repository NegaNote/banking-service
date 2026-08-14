#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
RUN_ID="$(date +%s)-$$"
USERNAME="idempotency-${RUN_ID}"
EMAIL="idempotency-${RUN_ID}@example.com"
PASSWORD='Password123!'

for command_name in curl jq sed awk; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        printf 'Missing required command: %s\n' "$command_name" >&2
        exit 1
    fi
done

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

HTTP_BODY=
HTTP_STATUS=
HTTP_HEADERS_FILE="$TEMP_DIR/response-headers"
H2_BODY=
H2_STATUS=
H2_COOKIE_JAR="$TEMP_DIR/h2-cookies"
H2_SESSION=

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    if [[ -n "$HTTP_BODY" ]]; then
        printf '%s\n' "$HTTP_BODY" >&2
    fi
    exit 1
}

request() {
    local method="$1"
    local path="$2"
    local token="$3"
    local payload="${4-}"
    local response
    local -a curl_args
    local -a extra_headers

    curl_args=(
        -sS
        -X "$method"
        "$BASE_URL$path"
        -H 'Accept: application/json'
    )

    if [[ -n "$token" ]]; then
        curl_args+=(-H "Authorization: Bearer $token")
    fi

    if [[ -n "$payload" ]]; then
        curl_args+=(-H 'Content-Type: application/json' --data "$payload")
    fi

    extra_headers=("${@:5}")
    for header in "${extra_headers[@]}"; do
        curl_args+=(-H "$header")
    done

    response="$(
        curl "${curl_args[@]}" \
            -D "$HTTP_HEADERS_FILE" \
            -w $'\n%{http_code}'
    )"
    HTTP_STATUS="${response##*$'\n'}"
    HTTP_BODY="${response%$'\n'*}"
}

expect_status() {
    local expected="$1"
    local description="$2"

    if [[ "$HTTP_STATUS" != "$expected" ]]; then
        fail "$description: expected HTTP $expected, got HTTP $HTTP_STATUS"
    fi
}

expect_status_any() {
    local description="$1"
    shift
    local expected

    for expected in "$@"; do
        if [[ "$HTTP_STATUS" == "$expected" ]]; then
            return
        fi
    done

    fail "$description: expected HTTP $*, got HTTP $HTTP_STATUS"
}

assert_json() {
    local filter="$1"
    local description="$2"
    shift 2

    if ! jq -e "$@" "$filter" <<<"$HTTP_BODY" >/dev/null 2>&1; then
        fail "$description"
    fi
}

assert_balance() {
    local expected="$1"
    local description="$2"

    assert_json \
        '.balance == ($expected | tonumber)' \
        "$description" \
        --arg expected "$expected"
}

canonical_json() {
    jq -cS . <<<"$1"
}

header_value() {
    local header_name="$1"

    awk -F': ' -v wanted="$header_name" '
        tolower($1) == tolower(wanted) {
            sub(/\r$/, "", $2)
            print $2
            exit
        }
    ' "$HTTP_HEADERS_FILE"
}

assert_header_value() {
    local header_name="$1"
    local expected="$2"
    local description="$3"
    local actual

    actual="$(header_value "$header_name")"
    if [[ "$actual" != "$expected" ]]; then
        fail "$description: expected $header_name: $expected, got: ${actual:-<missing>}"
    fi
}

assert_header_present() {
    local header_name="$1"
    local description="$2"

    if [[ -z "$(header_value "$header_name")" ]]; then
        fail "$description: missing $header_name header"
    fi
}

assert_header_absent() {
    local header_name="$1"
    local description="$2"

    if [[ -n "$(header_value "$header_name")" ]]; then
        fail "$description: unexpected $header_name header"
    fi
}

h2_login() {
    local landing_page
    local login_page
    local login_action
    local response
    local status

    landing_page="$TEMP_DIR/h2-landing.html"
    login_page="$TEMP_DIR/h2-login.html"

    curl -sS -c "$H2_COOKIE_JAR" "$BASE_URL/h2-console/" -o "$landing_page"
    H2_SESSION="$(
        sed -n "s/.*login\.jsp?jsessionid=\([^']*\).*/\1/p" "$landing_page" | head -n 1
    )"
    if [[ -z "$H2_SESSION" ]]; then
        fail 'H2 console did not provide a login session'
    fi

    curl -sS -b "$H2_COOKIE_JAR" -c "$H2_COOKIE_JAR" \
        "$BASE_URL/h2-console/login.jsp?jsessionid=$H2_SESSION" \
        -o "$login_page"
    login_action="$(
        sed -n 's/.*action="login\.do?jsessionid=\([^"]*\)".*/\1/p' "$login_page" | head -n 1
    )"
    if [[ -z "$login_action" ]]; then
        fail 'H2 console did not provide a login action'
    fi
    H2_SESSION="$login_action"

    response="$(
        curl -sS -b "$H2_COOKIE_JAR" -c "$H2_COOKIE_JAR" \
            -X POST "$BASE_URL/h2-console/login.do?jsessionid=$H2_SESSION" \
            --data-urlencode 'language=en' \
            --data-urlencode 'setting=Generic H2 (Embedded)' \
            --data-urlencode 'name=Generic H2 (Embedded)' \
            --data-urlencode 'driver=org.h2.Driver' \
            --data-urlencode 'url=jdbc:h2:mem:bankdb' \
            --data-urlencode 'user=sa' \
            --data-urlencode 'password=' \
            -w $'\n%{http_code}'
    )"
    status="${response##*$'\n'}"
    if [[ "$status" != 200 || "${response%$'\n'*}" != *'<frameset'* ]]; then
        fail 'H2 console login failed'
    fi
}

h2_query() {
    local sql="$1"
    local response

    response="$(
        curl -sS -b "$H2_COOKIE_JAR" -c "$H2_COOKIE_JAR" \
            -X POST "$BASE_URL/h2-console/query.do?jsessionid=$H2_SESSION" \
            --data-urlencode "sql=$sql" \
            -w $'\n%{http_code}'
    )"
    H2_STATUS="${response##*$'\n'}"
    H2_BODY="${response%$'\n'*}"

    if [[ "$H2_STATUS" != 200 || "$H2_BODY" == *'class="error"'* ]]; then
        fail "H2 query failed: $sql"
    fi
}

h2_scalar() {
    local alias="$1"
    local sql="$2"
    local value

    h2_query "$sql"
    value="$(
        sed -n "s/.*<th>$alias<\\/th><\\/tr><tr><td>\\([^<]*\\)<\\/td>.*/\\1/p" \
            <<<"$H2_BODY" | head -n 1
    )"
    if [[ -z "$value" ]]; then
        fail "H2 query did not return a value for $alias"
    fi
    printf '%s' "$value"
}

printf 'Testing API versions and idempotency at %s\n' "$BASE_URL"

request GET /api/v1/accounts ''
expect_status_any 'Unauthenticated v1 account list' 401 403
printf '[PASS] v1 account list requires authentication\n'

request POST /api/auth/register '' \
    "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
expect_status 201 'Register idempotency test user'
assert_json \
    '(.token | type) == "string" and (.token | length) > 0' \
    'Registration did not return a JWT'
TOKEN="$(jq -er '.token' <<<"$HTTP_BODY")"
printf '[PASS] Registered test user with a JWT\n'

request POST /api/v2/accounts "$TOKEN" '{}'
expect_status 200 'Open first v2 account'
FIRST_ACCOUNT="$(jq -er '.accountNumber' <<<"$HTTP_BODY")"

request POST /api/v2/accounts "$TOKEN" '{}'
expect_status 200 'Open second v2 account'
SECOND_ACCOUNT="$(jq -er '.accountNumber' <<<"$HTTP_BODY")"

request GET /api/v1/accounts "$TOKEN"
expect_status 200 'List v1 accounts'
assert_json \
    'length == 2 and ([.[].accountNumber] | sort) == ([$first, $second] | sort)' \
    'v1 account list was not the expected account list' \
    --arg first "$FIRST_ACCOUNT" \
    --arg second "$SECOND_ACCOUNT"
V1_ACCOUNTS="$(canonical_json "$HTTP_BODY")"
assert_header_value Deprecation true 'v1 deprecation header'
assert_header_present Sunset 'v1 sunset header'
printf '[PASS] v1 account list works with Deprecation and Sunset headers\n'

request GET /api/v2/accounts "$TOKEN"
expect_status 200 'List v2 accounts'
assert_json \
    'length == 2 and ([.[].accountNumber] | sort) == ([$first, $second] | sort)' \
    'v2 account list was not the expected account list' \
    --arg first "$FIRST_ACCOUNT" \
    --arg second "$SECOND_ACCOUNT"
V2_ACCOUNTS="$(canonical_json "$HTTP_BODY")"
if [[ "$V1_ACCOUNTS" != "$V2_ACCOUNTS" ]]; then
    fail 'v1 and v2 account-list response bodies differ'
fi
assert_header_absent Deprecation 'v2 account list'
assert_header_absent Sunset 'v2 account list'
printf '[PASS] v2 account list matches v1 without deprecation headers\n'

request POST "/api/v2/accounts/$FIRST_ACCOUNT/deposits" "$TOKEN" \
    '{"amount":100.00}'
expect_status 400 'Reject v2 deposit without Idempotency-Key'
printf '[PASS] v2 deposit without Idempotency-Key returns 400\n'

request POST "/api/v2/accounts/$FIRST_ACCOUNT/deposits" "$TOKEN" \
    '{"amount":100.00}' \
    'Idempotency-Key: abc-123'
expect_status 200 'Perform idempotent deposit'
assert_balance 100.00 'Initial idempotent deposit did not return balance 100.00'
DEPOSIT_RESPONSE="$(canonical_json "$HTTP_BODY")"

for retry in {1..5}; do
    request POST "/api/v2/accounts/$FIRST_ACCOUNT/deposits" "$TOKEN" \
        '{"amount":100.00}' \
        'Idempotency-Key: abc-123'
    expect_status 200 "Replay idempotent deposit retry $retry"
    if [[ "$(canonical_json "$HTTP_BODY")" != "$DEPOSIT_RESPONSE" ]]; then
        fail "Deposit retry $retry did not return the original response"
    fi
done

request GET "/api/v2/accounts/$FIRST_ACCOUNT" "$TOKEN"
expect_status 200 'Read balance after idempotent deposit retries'
assert_balance 100.00 'Idempotent deposit retries changed the balance'
printf '[PASS] Deposit replay returns the same response and changes balance only once\n'

h2_login
transaction_count="$(h2_scalar TX_COUNT 'SELECT COUNT(*) AS TX_COUNT FROM bank_transactions')"
if [[ "$transaction_count" != 1 ]]; then
    fail "Expected exactly one bank transaction after five deposit retries, got $transaction_count"
fi
printf '[PASS] H2 shows exactly one bank transaction after five deposit retries\n'

request POST "/api/v2/accounts/$FIRST_ACCOUNT/deposits" "$TOKEN" \
    '{"amount":200.00}' \
    'Idempotency-Key: abc-123'
expect_status 409 'Reject idempotency key reuse with a different amount'
assert_json \
    '.message | ascii_downcase | contains("different request")' \
    'Idempotency conflict did not have a clear message'
printf '[PASS] Reusing abc-123 with a different amount returns 409 Conflict\n'

request POST "/api/v2/accounts/$FIRST_ACCOUNT/transfers" "$TOKEN" \
    "{\"amount\":25.00,\"toAccountNumber\":\"$SECOND_ACCOUNT\",\"description\":\"Idempotent transfer\"}" \
    'Idempotency-Key: transfer-123'
expect_status 200 'Perform idempotent transfer'
assert_balance 75.00 'Initial idempotent transfer did not return balance 75.00'
TRANSFER_RESPONSE="$(canonical_json "$HTTP_BODY")"

for retry in {1..5}; do
    request POST "/api/v2/accounts/$FIRST_ACCOUNT/transfers" "$TOKEN" \
        "{\"amount\":25.00,\"toAccountNumber\":\"$SECOND_ACCOUNT\",\"description\":\"Idempotent transfer\"}" \
        'Idempotency-Key: transfer-123'
    expect_status 200 "Replay idempotent transfer retry $retry"
    if [[ "$(canonical_json "$HTTP_BODY")" != "$TRANSFER_RESPONSE" ]]; then
        fail "Transfer retry $retry did not return the original response"
    fi
done

request GET "/api/v2/accounts/$FIRST_ACCOUNT" "$TOKEN"
expect_status 200 'Read source balance after transfer retries'
assert_balance 75.00 'Idempotent transfer retries changed the source balance'

request GET "/api/v2/accounts/$SECOND_ACCOUNT" "$TOKEN"
expect_status 200 'Read destination balance after transfer retries'
assert_balance 25.00 'Idempotent transfer retries changed the destination balance'

transaction_count="$(h2_scalar TX_COUNT 'SELECT COUNT(*) AS TX_COUNT FROM bank_transactions')"
if [[ "$transaction_count" != 2 ]]; then
    fail "Expected one new transaction for the idempotent transfer, got $transaction_count total"
fi
printf '[PASS] Transfer replay is idempotent and adds one transaction only\n'

request POST "/api/v1/accounts/$FIRST_ACCOUNT/transfers" "$TOKEN" \
    "{\"amount\":10.00,\"toAccountNumber\":\"$SECOND_ACCOUNT\",\"description\":\"v1 transfer\"}"
expect_status 200 'Perform v1 transfer without Idempotency-Key'
assert_balance 65.00 'v1 transfer did not return balance 65.00'
assert_header_value Deprecation true 'v1 transfer deprecation header'
printf '[PASS] v1 transfer works without an Idempotency-Key\n'

request GET "/api/v2/accounts/$FIRST_ACCOUNT" "$TOKEN"
expect_status 200 'Read final source balance'
assert_balance 65.00 'Final source balance was not 65.00'

request GET "/api/v2/accounts/$SECOND_ACCOUNT" "$TOKEN"
expect_status 200 'Read final destination balance'
assert_balance 35.00 'Final destination balance was not 35.00'

request_count="$(h2_scalar RECORD_COUNT 'SELECT COUNT(*) AS RECORD_COUNT FROM idempotency_records')"
if [[ "$request_count" != 2 ]]; then
    fail "Expected two idempotency records, got $request_count"
fi

h2_query "SELECT * FROM idempotency_records WHERE IDEMPOTENCY_KEY = 'abc-123'"
for column in REQUEST_HASH RESPONSE_STATUS EXPIRES_AT; do
    if [[ "$H2_BODY" != *"<th>$column</th>"* ]]; then
        fail "SELECT * FROM idempotency_records did not include $column"
    fi
done

request_hash="$(
    h2_scalar REQUEST_HASH \
        "SELECT REQUEST_HASH AS REQUEST_HASH FROM idempotency_records WHERE IDEMPOTENCY_KEY = 'abc-123'"
)"
response_status="$(
    h2_scalar RESPONSE_STATUS \
        "SELECT RESPONSE_STATUS AS RESPONSE_STATUS FROM idempotency_records WHERE IDEMPOTENCY_KEY = 'abc-123'"
)"
expires_at="$(
    h2_scalar EXPIRES_AT \
        "SELECT EXPIRES_AT AS EXPIRES_AT FROM idempotency_records WHERE IDEMPOTENCY_KEY = 'abc-123'"
)"
if [[ -z "$request_hash" || "$response_status" != 200 || -z "$expires_at" ]]; then
    fail 'The abc-123 idempotency record is missing request_hash, response_status, or expires_at'
fi
printf '[PASS] H2 shows populated idempotency metadata for abc-123\n'

printf 'All API versioning and idempotency checks passed.\n'
