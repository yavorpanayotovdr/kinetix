#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROTO_ROOT="$(cd "$PROJECT_ROOT/../proto/src/main/proto" && pwd)"
OUT_DIR="$PROJECT_ROOT/src/kinetix_risk/proto"

mkdir -p "$OUT_DIR/kinetix/common" "$OUT_DIR/kinetix/risk"

# Generate Python protobuf and gRPC stubs
cd "$PROJECT_ROOT"
uv run python -m grpc_tools.protoc \
    --proto_path="$PROTO_ROOT" \
    --python_out="$OUT_DIR" \
    --pyi_out="$OUT_DIR" \
    --grpc_python_out="$OUT_DIR" \
    kinetix/common/types.proto \
    kinetix/risk/risk_calculation.proto

# Create __init__.py files for generated packages
touch "$OUT_DIR/__init__.py"
touch "$OUT_DIR/kinetix/__init__.py"
touch "$OUT_DIR/kinetix/common/__init__.py"
touch "$OUT_DIR/kinetix/risk/__init__.py"

echo "Proto stubs generated in $OUT_DIR"
