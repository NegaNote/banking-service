#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"

for command_name in curl jq; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        printf 'Missing required command: %s\n' "$command_name" >&2
        exit 1
    fi
done

HTTP_BODY=
HTTP_STATUS=

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

    if [[ -n "$payload" ]]; then
        response="$(
            curl -sS -X "$method" "$BASE_URL$path" \
                -H 'Accept: application/json' \
                -H 'Content-Type: application/json' \
                -H "Authorization: Bearer $token" \
                --data "$payload" \
                -w $'\n%{http_code}'
        )"
    else
        response="$(
            curl -sS -X "$method" "$BASE_URL$path" \
                -H 'Accept: application/json' \
                -H "Authorization: Bearer $token" \
                -w $'\n%{http_code}'
        )"
    fi

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

printf 'Testing controllers at %s\n' "$BASE_URL"

request POST /api/auth/register '' \
    '{"username":"matthew","email":"matthew@example.com","password":"Password123!"}'
expect_status 201 'Register Matthew'
assert_json \
    '(.token | type) == "string" and (.token | length) > 0' \
    'Matthew registration did not return a JWT'
MATTHEW_TOKEN="$(jq -er '.token' <<<"$HTTP_BODY")"
printf '[PASS] Register Matthew\n'

request POST /api/auth/register '' \
    '{"username":"alice","email":"alice@example.com","password":"Password123!"}'
expect_status 201 'Register Alice'
assert_json \
    '(.token | type) == "string" and (.token | length) > 0' \
    'Alice registration did not return a JWT'
ALICE_TOKEN="$(jq -er '.token' <<<"$HTTP_BODY")"
printf '[PASS] Register Alice\n'

request POST /api/accounts "$MATTHEW_TOKEN" '{}'
expect_status 200 'Open Matthew account 1'
MATTHEW_FIRST="$(jq -er '.accountNumber' <<<"$HTTP_BODY")"

request POST /api/accounts "$MATTHEW_TOKEN" '{}'
expect_status 200 'Open Matthew account 2'
MATTHEW_SECOND="$(jq -er '.accountNumber' <<<"$HTTP_BODY")"

request POST /api/accounts "$ALICE_TOKEN" '{}'
expect_status 200 'Open Alice account'
ALICE_ACCOUNT="$(jq -er '.accountNumber' <<<"$HTTP_BODY")"

request GET /api/accounts "$MATTHEW_TOKEN"
expect_status 200 "List Matthew's accounts"
assert_json \
    'length == 2 and ([.[].accountNumber] | sort) == ([$first, $second] | sort)' \
    "Matthew's account list did not contain exactly his two accounts" \
    --arg first "$MATTHEW_FIRST" \
    --arg second "$MATTHEW_SECOND"
printf '[PASS] Matthew has exactly two accounts\n'

request GET /api/accounts "$ALICE_TOKEN"
expect_status 200 "List Alice's accounts"
assert_json \
    'length == 1 and .[0].accountNumber == $account' \
    "Alice's account list did not contain exactly her one account" \
    --arg account "$ALICE_ACCOUNT"
printf '[PASS] Alice has exactly one account\n'

request POST "/api/accounts/$MATTHEW_FIRST/deposits" "$MATTHEW_TOKEN" \
    '{"amount":1000.00}'
expect_status 200 'Deposit 1000.00 into Matthew account 1'
assert_json \
    '.accountNumber == $account' \
    'Deposit response returned the wrong account' \
    --arg account "$MATTHEW_FIRST"
assert_balance 1000.00 'Deposit did not return balance 1000.00'
printf '[PASS] Deposit returns balance 1000.00\n'

request POST "/api/accounts/$MATTHEW_FIRST/withdrawals" "$MATTHEW_TOKEN" \
    '{"amount":250.00}'
expect_status 200 'Withdraw 250.00 from Matthew account 1'
assert_balance 750.00 'Withdrawal did not return balance 750.00'
printf '[PASS] Withdrawal returns balance 750.00\n'

request POST "/api/accounts/$MATTHEW_FIRST/withdrawals" "$MATTHEW_TOKEN" \
    '{"amount":10000.00}'
expect_status 422 'Reject Matthew overdraft'
assert_json \
    '.message | ascii_downcase | contains("insufficient funds")' \
    'Overdraft response did not contain an insufficient funds message'
printf '[PASS] Overdraft returns 422 with an insufficient funds message\n'

request POST "/api/accounts/$MATTHEW_FIRST/transfers" "$MATTHEW_TOKEN" \
    "{\"amount\":500.00,\"toAccountNumber\":\"$MATTHEW_SECOND\",\"description\":\"Internal transfer\"}"
expect_status 200 'Transfer 500.00 to Matthew account 2'
assert_balance 250.00 'Internal transfer did not return first account balance 250.00'

request GET "/api/accounts/$MATTHEW_SECOND" "$MATTHEW_TOKEN"
expect_status 200 'Read Matthew account 2 after internal transfer'
assert_balance 500.00 'Internal transfer did not credit second account to 500.00'
printf '[PASS] Internal transfer leaves balances 250.00 and 500.00\n'

request POST "/api/accounts/$MATTHEW_FIRST/transfers" "$MATTHEW_TOKEN" \
    "{\"amount\":100.00,\"toAccountNumber\":\"$ALICE_ACCOUNT\",\"description\":\"Transfer to Alice\"}"
expect_status 200 'Transfer 100.00 to Alice'
assert_balance 150.00 'Transfer to Alice did not return first account balance 150.00'

request GET "/api/accounts/$ALICE_ACCOUNT" "$ALICE_TOKEN"
expect_status 200 'Read Alice account after incoming transfer'
assert_balance 100.00 'Transfer to Alice did not credit her account to 100.00'
printf '[PASS] Transfer to Alice leaves balances 150.00 and 100.00\n'

request POST "/api/accounts/$MATTHEW_FIRST/transfers" "$MATTHEW_TOKEN" \
    "{\"amount\":1.00,\"toAccountNumber\":\"$MATTHEW_FIRST\"}"
expect_status 400 'Reject transfer to the same account'
printf '[PASS] Transfer to the same account returns 400\n'

request POST "/api/accounts/$ALICE_ACCOUNT/deposits" "$MATTHEW_TOKEN" \
    '{"amount":1.00}'
expect_status 404 'Reject Matthew deposit into Alice account'

request POST "/api/accounts/$ALICE_ACCOUNT/withdrawals" "$MATTHEW_TOKEN" \
    '{"amount":1.00}'
expect_status 404 'Reject Matthew withdrawal from Alice account'

request POST "/api/accounts/$ALICE_ACCOUNT/transfers" "$MATTHEW_TOKEN" \
    "{\"amount\":1.00,\"toAccountNumber\":\"$MATTHEW_SECOND\"}"
expect_status 404 'Reject Matthew transfer from Alice account'
printf '[PASS] Matthew cannot deposit, withdraw, or transfer using Alice account\n'

request GET "/api/accounts/$MATTHEW_FIRST/transactions" "$MATTHEW_TOKEN"
expect_status 200 "Read Matthew account 1 transaction history"
assert_json \
    'length == 4
     and all(.[]; .fromAccountNumber == $first)
     and (map(select(.type == "DEPOSIT" and .amount == 1000)) | length) == 1
     and (map(select(.type == "WITHDRAWAL" and .amount == 250)) | length) == 1
     and (map(select(.type == "TRANSFER" and .amount == 500 and .toAccountNumber == $second)) | length) == 1
     and (map(select(.type == "TRANSFER" and .amount == 100 and .toAccountNumber == $alice)) | length) == 1' \
    'Matthew account 1 history did not contain the deposit, withdrawal, and two outgoing transfers' \
    --arg first "$MATTHEW_FIRST" \
    --arg second "$MATTHEW_SECOND" \
    --arg alice "$ALICE_ACCOUNT"
printf '[PASS] Transaction history contains one deposit, one withdrawal, and two outgoing transfers\n'

request PUT "/api/accounts/$MATTHEW_FIRST/transactions/1" "$MATTHEW_TOKEN" '{}'
expect_status_any 'PUT transaction endpoint' 404 405
printf '[PASS] PUT transaction endpoint is unavailable (HTTP %s)\n' "$HTTP_STATUS"

printf 'All controller checks passed.\n'
