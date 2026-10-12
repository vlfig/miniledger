#!/usr/bin/env bash
# Demo: builds the jar, starts the server, exercises the API with curl, and stops the server.
# Minting account 0 is the only source of money; account 1 is created by the first transfer.
set -euo pipefail
cd "$(dirname "$0")"

PORT=${MINILEDGER_PORT:-8080}
URL=http://localhost:$PORT
JAR=target/scala-3.10.0/miniledger.jar

sbt -Dsbt.color=false assembly > /dev/null
MINILEDGER_PORT=$PORT java -jar "$JAR" > /dev/null &
SERVER=$!
trap 'kill $SERVER 2>/dev/null; wait $SERVER 2>/dev/null || true' EXIT

until curl -sf "$URL/health" > /dev/null; do sleep 0.2; done

echo "Balance of minting account 0:"
curl -s "$URL/0/balance"; echo

echo "Transfer 10.50 from 0 to 1 (accepted, applied asynchronously):"
curl -s -X POST "$URL/transfer" \
  -H 'Content-Type: application/json' \
  -d '{"from": 0, "to": 1, "amount": "10.50"}'; echo

sleep 0.5 # let the executor catch up; reads are eventually consistent

echo "Balance of account 0 (minting, goes negative):"
curl -s "$URL/0/balance"; echo

echo "Balance of account 1 (created by the transfer):"
curl -s "$URL/1/balance"; echo

echo "History of account 0 (newest first):"
curl -s "$URL/0/history"; echo

echo "History of account 1:"
curl -s "$URL/1/history"; echo
