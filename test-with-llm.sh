#!/bin/bash

# Test script to verify app starts WITH LLM binding
# This simulates Cloud Foundry environment with boneyard-genai service

echo "Starting Spring Metal WITH LLM service..."
echo "The chat icon SHOULD appear in the UI"
echo ""

# Set profiles with 'llm' (will be auto-detected from service binding)
export SPRING_PROFILES_ACTIVE=cloud,mcp,llm

# Set VCAP_SERVICES with genai service
export VCAP_SERVICES='{
  "postgresql": [
    {
      "name": "boneyard-db",
      "label": "postgresql",
      "tags": ["postgres", "database"],
      "credentials": {
        "uri": "postgresql://user:pass@localhost:5432/music"
      }
    }
  ],
  "user-provided": [
    {
      "name": "boneyard-genai",
      "label": "user-provided",
      "tags": ["llm", "genai"],
      "credentials": {
        "uri": "http://localhost:1234",
        "api-key": "test-key",
        "model-name": "qwen/qwen3-4b-2507",
        "model-capabilities": "chat,embedding"
      }
    }
  ]
}'

echo "Active profiles: $SPRING_PROFILES_ACTIVE"
echo "Services: Database + LLM service"
echo ""
echo "Starting application..."

./mvnw spring-boot:run