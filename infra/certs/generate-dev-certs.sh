#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Generating self-signed CA..."
openssl req -x509 -newkey rsa:4096 -keyout ca-key.pem -out ca-cert.pem \
    -days 365 -nodes -subj "/CN=Kinetix Dev CA"

echo "Generating server key and CSR..."
openssl req -newkey rsa:4096 -keyout server-key.pem -out server-csr.pem \
    -nodes -subj "/CN=localhost"

echo "Signing server certificate with CA..."
openssl x509 -req -in server-csr.pem -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem -days 365 \
    -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1")

rm -f server-csr.pem ca-cert.srl

echo "Generated files:"
echo "  CA certificate:     $SCRIPT_DIR/ca-cert.pem"
echo "  Server certificate: $SCRIPT_DIR/server-cert.pem"
echo "  Server private key: $SCRIPT_DIR/server-key.pem"
echo ""
echo "To enable gRPC TLS, set:"
echo "  GRPC_TLS_ENABLED=true"
echo "  GRPC_TLS_CERT=$SCRIPT_DIR/server-cert.pem"
echo "  GRPC_TLS_KEY=$SCRIPT_DIR/server-key.pem"
echo "  GRPC_TLS_CA=$SCRIPT_DIR/ca-cert.pem"
