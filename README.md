# 🎸 Spring Metal – Spring AI + Tanzu Demo

<p align="center">
  <span style="font-family:'JetBrains Mono', monospace; color:#8B5CF6;">Generative AI reference app that pairs Spring Boot with Tanzu Platform AI services.</span><br/>
  <sub><code>Spring Boot 3.4</code> · <code>Java 21</code> · <code>Spring AI 1.1.0-M1</code> · <code>PostgreSQL + pgvector</code></sub>
</p>

<p align="center">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.4.2-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" />
  <img alt="Spring AI" src="https://img.shields.io/badge/Spring%20AI-RAG%20Ready-0EA5E9?style=for-the-badge" />
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-15%20%2B%20pgvector-336791?style=for-the-badge&logo=postgresql&logoColor=white" />
  <img alt="Tanzu" src="https://img.shields.io/badge/Tanzu-Platform-8B5CF6?style=for-the-badge&logo=vmware" />
</p>

---

## 🔥 Feature Spotlight

| 🎯 Focus | 🌈 What You Get | 🛠️ Notes |
| --- | --- | --- |
| Retrieval-Augmented Generation | RAG endpoints via `MessageRetriever` and a pgvector-backed `VectorStore` | Runs whenever the `llm` profile is active |
| Multi-profile data access | Auto-configured JPA, Redis, MongoDB repositories | Profiles are auto-enabled via Cloud Foundry / Kubernetes bindings |
| MCP integration | Dynamic Streamable HTTP server registration via `/api/mcp/connections` | `/api/mcp/status` reflects live tool availability |
| Operational tooling | Crash-test endpoints (`/errors/**`), request diagnostics, application info | Useful for demos—lock down before production |

---

## 🚀 Quick Start

> 💡 **Tip:** Profiles default to `local,llm,mcp`. Override with `SPRING_PROFILES_ACTIVE` for other topologies.

<details>
  <summary><strong>🧑‍💻 Local Developer Loop</strong></summary>

  | ✅ Prerequisite | 🔍 Details |
  | --- | --- |
  | PostgreSQL 15 + pgvector | Default URL: `jdbc:postgresql://localhost:15432/music`, user `gpadmin` |
  | LLM endpoint | Defaults to `http://127.0.0.1:1234` (Ollama / local gateway) |
  | Java & Maven | OpenJDK 21, Maven 3.9+ |

  ```bash
  # start the database & vector extension (example using docker)
  docker run --rm --name music-db -e POSTGRES_USER=gpadmin -e POSTGRES_PASSWORD=secret \
    -p 15432:5432 ankane/pgvector:v0.7.2

  # build and run with local + LLM profiles
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,llm,mcp
  ```

  Once started:

  - Open `http://localhost:8080/albums` to browse seeded data
  - Test chat RAG: `curl -X POST http://localhost:8080/ai/chat -H 'Content-Type: application/json' -d '{"text":"Recommend a metal album"}'`
  - Validate embeddings: `GET /ai/test-embedding`
  - Register MCP servers on the fly: `curl -X POST http://localhost:8080/api/mcp/connections ...`
</details>

<details>
  <summary><strong>☁️ Tanzu Application Service (Cloud Foundry)</strong></summary>

  ```bash
  ./mvnw clean package
  cf login -u <ADMIN_USER> -p <ADMIN_PASSWORD>
  cf target -o <ORG> -s <SPACE>
  cf push
  ```

  - `manifest.yml` binds the app to `boneyard-genai` and `boneyard-db` services
  - `SPRING_PROFILES_ACTIVE` is set to `cloud,llm` during deployment
  - Ensure the space has access to Postgres (pgvector) and Tanzu GenAI services
</details>

---

## 🧭 Architecture At-a-Glance

| Layer | Component | Description |
| --- | --- | --- |
| API | `AlbumController`, `AIController`, `InfoController`, `ErrorController` | REST resources for CRUD, RAG, diagnostics, and demo failure modes |
| Data | `JpaAlbumRepository`, `RedisAlbumRepository`, `MongoAlbumRepository` | Repository implementations toggled by active profile |
| AI | `AiConfiguration`, `MessageRetriever`, `VectorStoreInitializer` | Bootstraps chat model + embeddings and hydrates pgvector |
| Platform | `SpringApplicationContextInitializer` | Detects bound services (CF + K8s) and activates matching profiles |

```
profiles: local | cloud | mysql | redis | mongodb | llm | mcp | http2
vector store: PostgreSQL + pgvector (local dims 768, cloud dims 1536)
```

---

## 📡 API Cheat Sheet

| Endpoint | Method | Profile(s) | Description |
| --- | --- | --- | --- |
| `/albums` | GET/PUT/POST/DELETE | all | CRUD operations on demo albums |
| `/ai/chat` | POST | `llm`, `mcp` | RAG-enabled chat response (deep-chat compatible)
| `/ai/rag` | POST | `llm`, `mcp` | Retrieve-only endpoint consuming `MessageRequest`
| `/ai/test-embedding` | GET | `llm` | Sends a test prompt through the configured `EmbeddingModel`
| `/ai/test-search` | GET | `llm` | Executes a similarity search through the vector store |
| `/api/mcp/status` | GET | `mcp` | Reports MCP tool availability |
| `/api/mcp/connections` | GET/POST | `mcp` | Manage Streamable HTTP MCP servers (list/create) |
| `/api/mcp/connections/{id}` | PUT/DELETE | `mcp` | Update or remove a registered server |
| `/api/mcp/connections/test` | POST | `mcp` | Validate connectivity without persisting |
| `/errors/kill` | GET | all | Forcibly exits the JVM (demo only!) |

> ⚠️ The `/errors/**` endpoints intentionally crash or destabilise the app – protect or remove for production.

---

## 🛠 MCP Runtime Settings

- Use `POST /api/mcp/connections/test` to verify a Streamable HTTP endpoint before saving it.
- Manage servers visually at `/#/mcp-settings`—the UI calls the same APIs to test, enable/disable, and delete connections.
- Register a server with `POST /api/mcp/connections` (payload: `{ "name": "audiodb", "baseUrl": "http://localhost:8090", "endpoint": "/api/mcp" }`).
- Toggle or update a server via `PUT /api/mcp/connections/{id}`; set `"enabled": false` to pause a connection.
- Remove a server with `DELETE /api/mcp/connections/{id}`; tools are dropped immediately.
- `GET /api/mcp/status` aggregates live health, so the RAG endpoints surface new tools without restart.

---

## 🧠 LLM & Vector Store Configuration

| Property | Local Default | Cloud Override |
| --- | --- | --- |
| Chat model | `qwen/qwen3-4b-2507` | Discovered via Tanzu GenAI bindings |
| Embedding model | `text-embedding-nomic-embed-text-v2` | Discovered via Tanzu GenAI bindings |
| pgvector dimensions | 768 | 1536 |
| Similarity search | Threshold `0.3`, `topK = 5` | Same defaults |

Review `src/main/resources/application.yml` for full profile breakdown.

---

## 🧪 Demo Recipes

1. **Seed & Inspect Albums** → Hit `/albums` after startup; `AlbumRepositoryPopulator` loads `albums.json` once.
2. **Populate Vector Store** → Triggered on `ApplicationReadyEvent` (and via `POST /ai/populate`). Watch logs for batch progress.
3. **Validate MCP** → Call `/api/mcp/status` to confirm `ToolCallbackProvider` wiring.

---

## 🤝 Contributing

We welcome bug reports, feature ideas, and pull requests.

- Run `./mvnw test` before submitting
- Add or update tests for new behaviour
- Describe profile-specific changes in your PR description

> 👀 Looking for something to tackle? Try hardening the `/errors/**` endpoints or improving resilience in `VectorStoreInitializer`.

---

## 📚 Related Docs

| 📘 Topic | 🔗 Link |
| --- | --- |
| Spring AI Reference | https://docs.spring.io/spring-ai/reference |
| Tanzu Application Service | https://docs.vmware.com/en/VMware-Tanzu-Application-Service |
| pgvector Extension | https://github.com/pgvector/pgvector |
