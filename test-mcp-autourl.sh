#!/bin/bash

# Test script for MCP auto-URL derivation
# Demonstrates how URL is automatically derived from service name

echo "🔧 Testing MCP Auto-URL Derivation"
echo "=================================="
echo ""

echo "1. Testing MINIMAL binding - only endpoint (URL auto-derived from service name):"
echo "Service name: 'mcp-server' → URL: 'http://mcp-server.apps.internal:8080'"
echo ""

export VCAP_SERVICES='{
  "user-provided": [
    {
      "name": "mcp-server",
      "label": "user-provided",
      "tags": ["mcp"],
      "credentials": {
        "endpoint": "/api/mcp"
      }
    }
  ]
}'

echo "VCAP_SERVICES configuration:"
echo "$VCAP_SERVICES" | jq .
echo ""

echo "Expected behavior:"
echo "  - Service detected as MCP (name contains 'mcp')"
echo "  - URL auto-derived: http://mcp-server.apps.internal:8080"
echo "  - Full URL: http://mcp-server.apps.internal:8080/api/mcp"
echo ""

# Set profiles
export SPRING_PROFILES_ACTIVE=cloud,mcp

echo "Starting Spring Metal with auto-URL derivation..."
echo "Check logs for: 'Auto-derived URL from service name'"
echo ""

./mvnw spring-boot:run