#!/usr/bin/env bash
set -euo pipefail

echo "[gate] running minimum backend test gate"
./gradlew \
  :auth-server:test \
  :ekyc-service:test \
  :tx-service:test \
  :vci-service:test \
  :gateway:test

echo "[gate] building frontend"
(
  cd frontend
  npm run build
)

echo "[gate] minimum test gate passed"
