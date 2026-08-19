#!/bin/sh
set -eu

api_base_url=$(printf '%s' "${API_BASE_URL:-http://localhost:8080}" | sed 's/[&|]/\\&/g')
telemetry_api_url=$(printf '%s' "${TELEMETRY_API_URL:-http://localhost:8081}" | sed 's/[&|]/\\&/g')

cat > /usr/share/nginx/html/config.js <<EOF
window.__PROCEL_CONFIG__ = {
  API_BASE_URL: "${api_base_url}",
  TELEMETRY_API_URL: "${telemetry_api_url}"
};
EOF
