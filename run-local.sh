#!/bin/bash

echo "Starting Spring Metal in local mode..."
echo "Make sure you have:"
echo "1. Local PostgreSQL with pgvector running on localhost:15432"
echo "2. LM Studio running on http://127.0.0.1:1234"
echo ""

export SPRING_PROFILES_ACTIVE=local,llm,mcp

./mvnw spring-boot:run