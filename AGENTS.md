# AGENTS.md

Guidance for agentic coding assistants working in this repository.

## Repository Overview

- This is `ai-chat-gateway`, an enterprise AI application platform.
- Backend: Java 17, Spring Boot 3.3, Maven, JPA, Redis, LangChain4j.
- Frontend: Vue 3, Vite 5, Element Plus, ECharts, plain JavaScript.
- Root package: `com.example.aichat`.
- Admin UI lives in `admin-ui/` and is built separately from the backend.
- There are no Cursor rules in `.cursor/rules/` or `.cursorrules` at time of writing.
- There is no `.github/copilot-instructions.md` at time of writing.
- If those files are added later, read them and merge their guidance into this file.

## Important Paths

- `pom.xml` - backend dependencies and Java version.
- `src/main/java/com/example/aichat/` - backend source code.
- `src/test/java/com/example/aichat/` - backend tests.
- `src/main/resources/db/schema.sql` - database initialization schema.
- `docs/DB_MIGRATION_MEMORY.md` - database migration rules and incident memory.
- `docs/ROADMAP_TODO.md` - backlog of discussed follow-up features and product/technical extensions.
- `admin-ui/package.json` - frontend scripts and dependencies.
- `admin-ui/src/api/` - Axios API wrappers.
- `admin-ui/src/views/` - Vue page components.
- `admin-ui/src/styles/nexus-theme.css` - primary UI theme variables and styles.
- `docker-compose.yml` - full local stack; `docker-compose-dev.yml` - MySQL and Redis only.

## Environment Requirements

- Use JDK 17 or newer and Maven from the system path; this repo has no Maven wrapper.
- Use Node.js 18 or newer and npm; the frontend has `package-lock.json` and no pnpm/yarn lockfile.
- Backend runtime expects MySQL, Redis, and pgvector for full functionality.
- Model and tool integrations read keys from environment variables; never hard-code new secrets.

## Backend Commands

- Run the backend app: `mvn spring-boot:run`.
- Run with dev profile: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
- Compile backend: `mvn compile`.
- Package backend and run tests: `mvn clean package`.
- Package backend without tests: `mvn clean package -DskipTests`.
- Run all backend tests: `mvn test`.
- Run one test class: `mvn -Dtest=AiChatApplicationTests test`.
- Run one test method: `mvn -Dtest=AiChatApplicationTests#contextLoads test`.
- Run matching test classes: `mvn -Dtest='*ServiceTest' test`.
- There is no configured backend lint, formatter, or Checkstyle plugin.
- Prefer targeted Maven tests before broader `mvn test` when iterating.

## Frontend Commands

- Install frontend dependencies: `npm ci` from `admin-ui/`.
- Start Vite dev server: `npm run dev` from `admin-ui/`.
- Build frontend: `npm run build` from `admin-ui/`.
- Preview production build: `npm run preview` from `admin-ui/`.
- Vite dev server runs on port `3000` and proxies `/api` to `http://localhost:8080`.
- There are no frontend lint or test scripts in `package.json`.
- Do not invent lint/test commands unless you add the supporting tooling.

## Docker Commands

- Start full stack: `docker compose up -d`.
- Start dev databases only: `docker compose -f docker-compose-dev.yml up -d`.
- Stop containers: `docker compose down`; rebuild with `docker compose up -d --build`.
- Docker builds skip backend tests and build the frontend with `npm ci && npm run build`.
- Full stack exposes backend on `8080` and nginx frontend on `8081`.

## Backend Architecture

- Keep controllers thin; put business logic in services and persistence behind repositories.
- Use DTO records for simple request/response shapes where existing code does.
- Put JPA entities under `model/entity`, RAG entities under `rag/model`, Agent entities under `agent/model`.
- Preserve module boundaries: core chat, RAG, and Agent code are separate areas.
- Use constructor injection in new Spring components; do not copy existing field injection.
- Keep API routes under `/api` unless matching an existing module-specific pattern.

## Java Style

- Use 4-space indentation.
- Target Java 17 language features only.
- Use package names under `com.example.aichat`.
- Class names use `PascalCase`.
- Methods, fields, and local variables use `camelCase`.
- Constants use `UPPER_SNAKE_CASE` and are `static final`.
- Repository method names should follow Spring Data query derivation conventions.
- Prefer clear names over abbreviations; avoid one-letter variables except in tiny lambdas.
- Use Lombok `@Data` on JPA/config classes when consistent with nearby code.
- Use records for immutable DTOs when no setters are needed.
- Keep Java comments sparse; prefer self-explanatory code.

## Java Imports and Formatting

- Order imports as project imports, third-party imports, then `java.*` imports when editing manually.
- Avoid wildcard imports in new code.
- Keep a blank line between import groups if the file already uses groups.
- Match local wrapping style for constructor parameters, chains, and stream operations.
- Do not reformat entire files; there is no formatter config, so minimize formatting-only diffs.
- Use ASCII for new files unless the file already contains Chinese text or user-facing Chinese copy is needed.

## Error Handling and Logging

- Use SLF4J `LoggerFactory.getLogger(...)` for backend logging.
- Prefer structured log messages with `{}` placeholders.
- Do not log raw API keys, bearer tokens, model secrets, uploaded document contents, or full sensitive prompts.
- Existing API-key failures return JSON shaped like `{"error":{"message":"...","code":401}}`; preserve it.
- Use specific exceptions such as `IllegalArgumentException` for bad input where existing services do.
- Wrap checked IO/serialization failures with context, but do not swallow exceptions silently.
- For SSE endpoints, call `completeWithError` on send or model failures.
- Always clean ThreadLocal request context in completion hooks when adding interceptors or async flows.

## Persistence and Data

- JPA repositories extend `JpaRepository`; table and column names use snake_case annotations where needed.
- Entities usually keep `createdAt` and `updatedAt` with lifecycle hooks.
- Do not change `ddl-auto` casually; schema is managed by SQL plus Hibernate `update`.
- For existing or production databases, do not rely on `schema.sql` or Hibernate `ddl-auto` for field changes; read `docs/DB_MIGRATION_MEMORY.md` and provide explicit migration SQL.
- Keep MySQL for core relational data and pgvector/Postgres for vector storage.
- Keep Redis scoped to rate limiting, sessions, or cache-like state.

## Frontend Style

- Use Vue 3 single-file components with `<script setup>`.
- Use plain JavaScript, not TypeScript, unless explicitly adding TypeScript support.
- Use 2-space indentation in Vue, JS, and CSS files.
- Imports do not use semicolons in existing frontend code.
- Prefer single quotes in frontend JavaScript and use `@/` imports from `admin-ui/src`.
- Keep API calls in `src/api/*.js`, not directly embedded across components.
- Axios wrapper in `src/api/index.js` injects `Authorization: Bearer <apiKey>` from localStorage.
- Let pages handle 401 when they need custom UX; global interceptor suppresses 401 popups.
- Use Element Plus components consistently and preserve the NEXUS visual system.
- For charts, use `nexus` and `nexus-light` themes and dispose instances in `onUnmounted`.
- Keep responsive CSS at the bottom of scoped styles when adding it.

## API and UX Conventions

- Backend API endpoints generally return plain DTOs, maps, or entity-derived views.
- Frontend API helpers should return the Axios wrapper promise directly.
- Use REST-like naming already present: `/rag/kb`, `/agents`, `/sessions`, etc.
- Preserve Chinese user-facing labels in the admin UI unless changing a full feature area.
- Keep API-key-protected views able to show an auth warning instead of crashing.
- Streaming chat endpoints use SSE and send `[DONE]` at completion.
- Long uploads and imports need extended Axios timeouts where appropriate.

## Testing Guidance

- Existing tests are minimal and use JUnit 5 with `@SpringBootTest`.
- Add backend tests under `src/test/java` mirroring the package under test.
- Prefer unit tests for pure services and focused Spring tests only when framework wiring matters.
- Mock databases and external APIs when possible; do not require real model API keys.
- Use `mvn -Dtest=ClassName#methodName test` for the fastest single-test loop.
- For frontend behavior changes, validate with `npm run build` unless tests are added.
- Do not add a new test framework without also adding scripts and minimal documentation.

## Configuration and Secrets

- Main env vars include `MYSQL_PASSWORD`, model API keys, `SERPER_API_KEY`, `RERANK_API_KEY`, `PGVECTOR_PASSWORD`, and `DASHSCOPE_MEMORY_*`.
- Prefer `${ENV_VAR:default}` placeholders in YAML config.
- Do not commit `.env` files or generated secret material.
- Never print full secrets in logs, test output, docs, or final responses.
- If touching Docker Compose, replace literal secret values with environment placeholders when feasible.

## Agent Workflow Rules

- Before editing, inspect nearby files and follow their local conventions.
- Keep changes minimal and focused on the requested task.
- Do not revert unrelated user changes in a dirty worktree.
- Use `rg` or specialized search tools to find references before renaming APIs or models.
- Update README or this file when adding new commands, scripts, profiles, or test tooling.
- Prefer root-cause fixes over superficial patches.
- Do not create commits or branches unless explicitly asked.
- Run the most specific relevant verification command available before handing off.
