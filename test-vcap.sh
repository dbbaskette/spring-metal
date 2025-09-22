#!/bin/bash

# Test script for MCP Cloud Foundry service binding
# This simulates a user-provided service binding in VCAP_SERVICES

export VCAP_SERVICES='{
  "user-provided": [
    {
      "name": "mcp-server",
      "label": "user-provided",
      "tags": ["mcp", "model-context-protocol"],
      "credentials": {
        "url": "http://localhost:8090",
        "endpoint": "/api/mcp"
      }
    }
  ]
}'

# Enable Cloud Foundry profile to trigger the configuration
export SPRING_PROFILES_ACTIVE=cloud,llm,mcp

echo "Starting Spring Metal with simulated VCAP_SERVICES..."
echo "MCP Server Configuration:"
echo "  - URL: http://localhost:8090"
echo "  - Endpoint: /api/mcp"
echo ""

./mvnw spring-boot:run