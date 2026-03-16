# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules (from project root)
./mvnw install -DskipTests

# Run tests
./mvnw test

# Run a single test class
./mvnw test -pl core -Dtest=CommandLineToolTest

# Run the core CLI application
./mvnw spring-boot:run -pl core

# Run the gateway server (port 8881)
# First time or after package changes: clean install everything first
mvn clean install -DskipTests
mvn spring-boot:run -pl gateway
# Or via fat jar after build:
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

## Project Structure

Multi-module Maven project (`com.chung.ai.software`, Java 17, Spring Boot 3.4.3, LangChain4j 1.0.0-beta1, Lombok):

- **`core/`** — Standalone CLI chat bot. Contains `ChatAgent`, `ChatAgentFactory`, AI config, and built-in tools (CommandLine, FileManagement, HttpRequest). MCP client support via stdio and HTTP transports.
- **`gateway/`** — Spring Boot HTTP server (port 8881) acting as a message gateway between MS Teams and AI agents. Depends on `core`.

## Architecture: Gateway Module

The gateway follows an **event-driven, async dispatch** pattern:

1. **Input**: `TeamsController` (`POST /api/messages`) accepts Bot Framework activity events, wraps them as `GatewayEvent`, and enqueues them — immediately returning `202 Accepted`.
2. **Queue**: `EventQueue` (in-memory) buffers events.
3. **Dispatcher**: `AgentDispatcher` polls the queue every 100ms via `@Scheduled`, then fans out each event to a virtual-thread via `CompletableFuture.runAsync`.
4. **Session**: `SessionRegistry` manages `AgentSession`s keyed by Teams `conversationId`. Sessions are created on demand.
5. **Routing**: Each `AgentSession` supports in-chat commands (`/use <agentName>`, `/agents`, `@agentName message`) and routes messages to lazily-instantiated `ChatAgent` instances (one per agent-name per session, with isolated 200-message memory).
6. **Agent Registry**: `AgentRegistry` holds named `AgentDefinition`s. The `"default"` agent is always present. Additional agents can be registered via `AgentManagementController` (`/api/agents`).
7. **Reply**: After the agent responds, `TeamsReplyService` posts the reply back to Teams via the Bot Framework reply URL.

Event types: `MESSAGE`, `HEARTBEAT`, `CRON`, `HOOK`, `WEBHOOK`.

## Configuration

**Core** (`core/src/main/resources/application.yaml`):
- `app.ai.local: true` → use Ollama; `false` → use OpenAI

**Gateway** (`gateway/src/main/resources/application.yaml`):
- `app.ai.local: false` by default (OpenAI)
- `gateway.teams.verify-token: true` — JWT verification toggle (currently a TODO for production)
- `gateway.heartbeat.interval-ms: 1800000` (30 min)

Key environment variables:

| Variable | Description |
|---|---|
| `OPENAI_API_KEY` | OpenAI API key |
| `OPENAI_MODEL` | Model name (default: `gpt-4o-mini`) |
| `OLLAMA_BASE_URL` | Ollama URL (default: `http://localhost:11434`) |
| `OLLAMA_MODEL` | Ollama model (default: `llama3.3`) |
| `MICROSOFT_APP_ID` / `MICROSOFT_APP_PASSWORD` / `MICROSOFT_TENANT_ID` | Bot Framework credentials |
| `TAVILY_API_KEY` | Web search via Tavily |

## In-Chat Commands (Gateway)

- `/use <agentName>` — permanently switch active agent for the session
- `/agents` — list all registered agents
- `@<agentName> <message>` — one-shot message to a specific agent without switching

## Webhook Input Mechanism

External systems trigger agent tasks via `POST /api/webhooks/{webhookId}` with any payload body. The gateway resolves the `WebhookDefinition`, builds an agent prompt from a template, runs the agent asynchronously, and optionally delivers the result.

**Register a webhook** (`POST /api/webhook-definitions`):
```json
{
  "name": "ci-failure-alert",
  "description": "GitHub Actions failure handler",
  "agentName": "default",
  "promptTemplate": "A CI pipeline failure occurred. Analyze and summarize:\n{{payload}}",
  "outputTarget": "LOG"
}
```
Supported output targets: `NONE` (discard), `LOG` (info log), `REPLY_URL` (HTTP POST result as JSON).

Prompt template placeholders: `{{payload}}`, `{{webhookName}}`, `{{timestamp}}`.

**Trigger a webhook**: `POST /api/webhooks/{id}` — any content-type, any body.

**Webhook package**: `gateway/.../gateway/webhook/` — `WebhookDefinition`, `WebhookContext`, `WebhookRegistry`, `WebhookOutputService`.

Each webhook gets an isolated session `"webhook:{id}"` by default, or can share a session by setting `sessionId`.

## Known TODOs

- JWT verification for Bot Framework tokens (`TeamsController`)
- `CRON` handler in `AgentDispatcher` is still a stub
- `HeartbeatScheduler` only logs; proactive prompts not yet implemented
- `ChatAgent.refreshMcpTools()` is a no-op (LangChain4j 1.0.0-beta1 limitation)
- `WebhookRegistry` is in-memory only; definitions are lost on restart
