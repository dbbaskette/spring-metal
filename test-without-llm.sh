#!/bin/bash

# Test script to verify app starts without LLM binding
# This simulates Cloud Foundry environment without boneyard-genai service

echo "Starting Spring Metal WITHOUT LLM service..."
echo "The chat icon should NOT appear in the UI"
echo ""

# Set profiles without 'llm'
export SPRING_PROFILES_ACTIVE=cloud,mcp

# Optionally set VCAP_SERVICES with only database service (no genai)
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
  ]
}'

echo "Active profiles: $SPRING_PROFILES_ACTIVE"
echo "Services: Only database (no LLM service)"
echo ""
echo "Starting application..."

./mvnw spring-boot:run