#!/bin/bash

# Test script for MCP with custom port and protocol
# Demonstrates advanced configuration options

echo "🔧 Testing MCP Custom Port and Protocol"
echo "======================================="
echo ""

echo "3. Testing CUSTOM PORT/PROTOCOL binding:"
echo "Hostname: 'secure-mcp' + HTTPS + Port 9443"
echo ""

export VCAP_SERVICES='{
  "user-provided": [
    {
      "name": "secure-mcp-service",
      "label": "user-provided",
      "tags": ["model-context-protocol"],
      "credentials": {
        "hostname": "secure-mcp",
        "port": "9443",
        "protocol": "https",
        "endpoint": "/api/v1/mcp",
        "apiKey": "test-api-key-123"
      }
    }
  ]
}'

echo "VCAP_SERVICES configuration:"
echo "$VCAP_SERVICES" | jq .
echo ""

echo "Expected behavior:"
echo "  - Service detected as MCP (tags contain 'model-context-protocol')"
echo "  - URL derived: https://secure-mcp.apps.internal:9443"
echo "  - Full URL: https://secure-mcp.apps.internal:9443/api/v1/mcp"
echo "  - Auth header: 'Authorization: Bearer test-api-key-123'"
echo ""

# Set profiles
export SPRING_PROFILES_ACTIVE=cloud,mcp

echo "Starting Spring Metal with custom port/protocol..."
echo "Check logs for: 'Derived URL from hostname'"
echo ""

./mvnw spring-boot:run