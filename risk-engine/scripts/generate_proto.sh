#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROTO_ROOT="$(cd "$PROJECT_ROOT/../proto/src/main/proto" && pwd)"
OUT_DIR="$PROJECT_ROOT/src"

mkdir -p "$OUT_DIR/kinetix/common" "$OUT_DIR/kinetix/risk"

# Generate Python protobuf and gRPC stubs
cd "$PROJECT_ROOT"
uv run python -m grpc_tools.protoc \
    --proto_path="$PROTO_ROOT" \
    --python_out="$OUT_DIR" \
    --pyi_out="$OUT_DIR" \
    --grpc_python_out="$OUT_DIR" \
    kinetix/common/types.proto \
    kinetix/risk/risk_calculation.proto \
    kinetix/risk/stress_testing.proto \
    kinetix/risk/market_data_dependencies.proto \
    kinetix/risk/regulatory_reporting.proto \
    kinetix/risk/ml_prediction.proto

echo "Proto stubs generated in $OUT_DIR/kinetix/"
