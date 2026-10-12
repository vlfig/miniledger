#!/usr/bin/env bash
# Demo: start the server first (`sbt run`), then run this script.
# Minting account 0 is the only source of money; account 1 is created by the first transfer.
set -euo pipefail

URL=${URL:-http://localhost:8080}

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
