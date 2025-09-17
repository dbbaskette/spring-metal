# Spring Metal Deployment Guide

## Prerequisites

### For Local Development
1. PostgreSQL with pgvector extension (Greenplum) running on `localhost:15432`
   - Database name: `music`
   - Username: `gpadmin`
   - Password: (set via `DB_PASSWORD` env variable if needed)

2. LM Studio running on `http://127.0.0.1:1234`
   - Chat model: `qwen/qwen3-4b-2507`
   - Embeddings model: `text-embedding-nomic-embed-text-v2`

### For Cloud Foundry Deployment
- Cloud Foundry with bound services:
  - `boneyard-genai` (AI service)
  - `boneyard-db` (Database with pgvector)

## Running Locally

### Option 1: Using the startup script
```bash
./run-local.sh
```

### Option 2: Direct Maven command
```bash
export SPRING_PROFILES_ACTIVE=local,llm
./mvnw spring-boot:run
```

### Option 3: Running the JAR
```bash
./mvnw clean package
java -jar target/spring-metal-0.5.jar --spring.profiles.active=local,llm
```

The application will start on `http://localhost:8080`

## Deploying to Cloud Foundry

### Build the application
```bash
./mvnw clean package
```

### Deploy to Cloud Foundry
```bash
cf push
```

The `manifest.yml` is configured to:
- Activate `cloud` and `llm` profiles
- Bind to `boneyard-genai` and `boneyard-db` services
- Use Java 17

## Configuration Details

### Profiles
- `local`: Activates local development configuration
  - Uses local PostgreSQL at `localhost:15432`
  - Uses LM Studio at `http://127.0.0.1:1234`
  - Configured for 768-dimension embeddings (nomic-embed-text)

- `cloud`: Activates Cloud Foundry configuration
  - Uses bound services via java-cfenv
  - Configured for 1536-dimension embeddings (OpenAI)

- `llm`: Required for AI features (use with either local or cloud)

### Database Configuration
- Local: PostgreSQL/Greenplum with pgvector at `localhost:15432`
- Cloud: Bound database service with pgvector support

### AI Models
- Local:
  - Chat: `qwen/qwen3-4b-2507`
  - Embeddings: `text-embedding-nomic-embed-text-v2`
- Cloud: Configured via bound GenAI service

## Spring AI 1.1.0-M1
This application uses Spring AI 1.1.0-M1 milestone release. The milestone repository is already configured in `pom.xml`.

## Troubleshooting

### Local Development Issues
1. Ensure PostgreSQL is running: `psql -h localhost -p 15432 -U gpadmin -d music`
2. Ensure LM Studio is running: `curl http://127.0.0.1:1234/v1/models`
3. Check logs for connection errors

### Cloud Foundry Issues
1. Verify services are bound: `cf services`
2. Check application logs: `cf logs spring-metal --recent`
3. Ensure services have pgvector support enabled