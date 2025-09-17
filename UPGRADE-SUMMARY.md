# Spring Metal Upgrade Summary

## Completed Upgrades

### 1. Spring AI 1.1.0-M1 Upgrade
- Updated from Spring AI 1.0.0-M6 to 1.1.0-M1
- Updated Maven dependencies:
  - Changed `spring-ai-openai-spring-boot-starter` to `spring-ai-starter-model-openai`
  - Changed `spring-ai-pgvector-store-spring-boot-starter` to `spring-ai-starter-vector-store-pgvector`
  - Added `spring-ai-advisors-vector-store` for RAG support
- Fixed import for `QuestionAnswerAdvisor`: now `org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor`

### 2. Local Development Mode
- Configured dual-mode operation: local and cloud
- **Local mode** (`local` profile):
  - Database: PostgreSQL/Greenplum at `localhost:15432` (user: `gpadmin`)
  - AI Models: LM Studio at `http://127.0.0.1:1234`
    - Chat: `qwen/qwen3-4b-2507`
    - Embeddings: `text-embedding-nomic-embed-text-v2` (768 dimensions)
  - Run with: `./run-local.sh` or `SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run`

### 3. Cloud Foundry Production Mode
- **Cloud mode** (`cloud` profile):
  - Automatically uses bound CF services
  - Services: `boneyard-genai` and `boneyard-db`
  - Embeddings: 1536 dimensions (for OpenAI compatibility)
  - Deploy with: `./mvnw clean package && cf push`

### 4. Simplified Profile Structure
- Removed unnecessary `llm` profile - AI features are now always enabled
- Profiles are now simply: `local` or `cloud`
- Default profile is `local` for easier development

## Configuration Files

### application.yml
- Separate configurations for local and cloud profiles
- Local profile includes full AI model configuration for LM Studio
- Cloud profile relies on CF service bindings

### manifest.yml
- Configured for production deployment
- Sets `SPRING_PROFILES_ACTIVE=cloud`
- Binds to required CF services

### run-local.sh
- Convenience script for local development
- Sets up environment for local mode
- Includes helpful reminders about prerequisites

## Key Benefits

1. **Seamless Environment Switching**: Just change the profile to switch between local and cloud
2. **Cost-Effective Development**: Use free local models during development
3. **Production Ready**: Cloud deployment uses enterprise services
4. **Latest Spring AI**: Leverages new features in 1.1.0-M1
5. **Simplified Configuration**: No need to remember multiple profiles

## Testing

To verify the setup:
1. **Local**: Ensure PostgreSQL (port 15432) and LM Studio (port 1234) are running, then use `./run-local.sh`
2. **Cloud**: Build with `./mvnw clean package` and deploy with `cf push`