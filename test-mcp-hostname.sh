#!/bin/bash

# Test script for MCP hostname-based URL derivation
# Demonstrates hostname field usage

echo "🔧 Testing MCP Hostname-based URL Derivation"
echo "============================================="
echo ""

echo "2. Testing HOSTNAME binding - hostname + endpoint (URL derived from hostname):"
echo "Hostname: 'my-mcp-app' → URL: 'http://my-mcp-app.apps.internal:8080'"
echo ""

export VCAP_SERVICES='{
  "user-provided": [
    {
      "name": "my-mcp-service",
      "label": "user-provided",
      "tags": ["mcp"],
      "credentials": {
        "hostname": "my-mcp-app",
        "endpoint": "/api/mcp"
      }
    }
  ]
}'

echo "VCAP_SERVICES configuration:"
echo "$VCAP_SERVICES" | jq .
echo ""

echo "Expected behavior:"
echo "  - Service detected as MCP (tags contain 'mcp')"
echo "  - URL derived from hostname: http://my-mcp-app.apps.internal:8080"
echo "  - Full URL: http://my-mcp-app.apps.internal:8080/api/mcp"
echo ""

# Set profiles
export SPRING_PROFILES_ACTIVE=cloud,mcp

echo "Starting Spring Metal with hostname-based URL derivation..."
echo "Check logs for: 'Derived URL from hostname'"
echo ""

./mvnw spring-boot:run