# OpenClaw4J

OpenClaw4J is a Spring Boot AI agent platform powered by [LangChain4j](https://github.com/langchain4j/langchain4j). It supports both OpenAI and local models via Ollama, and exposes AI agents through multiple input channels — an interactive CLI, Microsoft Teams, and an event-driven webhook system.

## Modules

| Module | Description |
|---|---|
| `core` | Standalone interactive CLI chat bot |
| `gateway` | HTTP server (port 8881) — Teams bot + webhook automation |

---

## Core Module — Interactive CLI

### Features

- **Multi-Agent Sessions:** Run multiple independent agents simultaneously and switch between them.
- **AI Model Options:** OpenAI GPT-4o-mini (default) or local models via [Ollama](https://ollama.com/).
- **Built-in Tools:**
  - **CommandLineTool** — execute OS commands (stdout/stderr captured)
  - **FileManagementTool** — read, write, append, delete, list files and directories
  - **HttpRequestTool** — GET, POST, PUT, DELETE requests
- **Web Search:** Tavily integration (if configured).
- **MCP Support:** Connect to external tools via Model Context Protocol (stdio or HTTP transport).

### Prerequisites

- Java 17+
- Maven 3.8+
- Ollama (optional) or an OpenAI API key

### Configuration (`core/src/main/resources/application.yaml`)

| Property | Environment Variable | Default | Description |
|---|---|---|---|
| `app.ai.local` | — | `true` | `true` = Ollama, `false` = OpenAI |
| `app.ai.ollama.model` | `OLLAMA_MODEL` | `llama3.3` | Ollama model name |
| `app.ai.ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `openai.api-key` | `OPENAI_API_KEY` | — | OpenAI API key |
| `openai.model` | `OPENAI_MODEL` | `gpt-4o-mini` | OpenAI model |
| `TAVILY_API_KEY` | `TAVILY_API_KEY` | — | Tavily web search key |

### Running

```bash
mvn clean install -DskipTests   # build from project root first
mvn spring-boot:run -pl core
```

### CLI Commands

| Command | Description |
|---|---|
| (type message) | Chat with the active agent |
| `/agents` | Create a new agent |
| `/list` | List all agents |
| `/use <index>` | Switch active agent |
| `exit` / `quit` | Exit |

### Tests

```bash
mvn test -pl core
# or a single class:
mvn test -pl core -Dtest=CommandLineToolTest
```

---

## Gateway Module — HTTP Server

The gateway is an event-driven server that routes messages from external systems to AI agents and sends responses back. All inputs are normalized into `GatewayEvent`s, queued, and dispatched asynchronously — so the HTTP layer is never blocked by AI processing.

### Input Channels

| Channel | Endpoint | Description |
|---|---|---|
| **MS Teams** | `POST /api/messages` | Microsoft Bot Framework webhook |
| **Webhooks** | `POST /api/webhooks/{id}` | External system triggers (CI, monitoring, etc.) |
| **Cron Jobs** | `POST /api/cron-definitions` | Scheduled recurring agent tasks |

### Key Features

- **Event-driven dispatch:** `AgentDispatcher` polls an in-memory queue every 100ms and fans out each event to a virtual thread.
- **Per-session isolation:** Each conversation gets its own `AgentSession` with independent 200-message memory per agent.
- **Lazy agent creation:** Agent instances are created on first use within a session.
- **Multi-agent routing:** In-chat commands work across all input channels.
- **Virtual threads:** Handles high concurrency with minimal overhead.

### In-Chat Commands (Teams & Webhooks)

| Command | Description |
|---|---|
| `/new` | Clear all agent instances in the session and start fresh |
| `/reset` | Clear the current agent's memory only |
| `/stop` | Stop the session (fires COMMAND_STOP hook) |
| `/use <agentName>` | Permanently switch the session's active agent |
| `/agents` | List all registered agents |
| `@<agentName> <message>` | One-shot message to a specific agent without switching |

### Agent Management API

Register custom agents with different personas at runtime:

```bash
# Register an agent
POST /api/agents
{ "name": "security", "description": "You are a security expert. Analyze events for threats." }

# List agents
GET /api/agents

# Delete an agent
DELETE /api/agents/{name}
```

---

## Webhook System

Webhooks allow external systems to trigger AI agent tasks automatically without a human in the loop. A webhook is an HTTP endpoint that another application calls when something happens — a CI failure, a monitoring alert, a new issue, etc.

### How It Works

```
External system → POST /api/webhooks/{id}
                       ↓
              WebhookController (202 immediately)
                       ↓
                  EventQueue
                       ↓
             AgentDispatcher (async)
                       ↓
          prompt = template.replace({{payload}}, body)
                       ↓
               AgentSession.chatWithAgent()
                       ↓
            WebhookOutputService → LOG / REPLY_URL / NONE
```

### Webhook Management API

```bash
# Register a webhook
POST /api/webhook-definitions
{
  "name": "ci-failure-alert",
  "description": "GitHub Actions failure handler",
  "agentName": "default",
  "promptTemplate": "A CI build failed. Summarize and suggest a fix:\n{{payload}}",
  "outputTarget": "LOG"
}
# → returns { "id": "abc-123", ... }

# List all webhooks
GET /api/webhook-definitions

# Get one
GET /api/webhook-definitions/{id}

# Delete
DELETE /api/webhook-definitions/{id}
```

### Trigger a Webhook

```bash
POST /api/webhooks/{id}
Content-Type: application/json

{ "repo": "my-app", "branch": "main", "status": "failed", "step": "unit-tests" }
```

Returns `202 Accepted` immediately. The agent processes the payload asynchronously.

### Prompt Template Placeholders

| Placeholder | Value |
|---|---|
| `{{payload}}` | Raw request body |
| `{{webhookName}}` | The webhook's `name` field |
| `{{timestamp}}` | ISO-8601 timestamp of the event |

### Output Targets

| Target | Behaviour |
|---|---|
| `LOG` | Agent response written to application log (default) |
| `REPLY_URL` | Agent response HTTP POSTed as JSON to `replyUrl` |
| `NONE` | Response discarded (fire-and-forget) |

**`REPLY_URL` POST body:**
```json
{ "webhookId": "...", "webhookName": "...", "result": "...agent response..." }
```

### Session Isolation

Each webhook gets a dedicated session `webhook:{id}` by default, giving it its own persistent conversation memory. Set `sessionId` on the definition to share context with another session.

---

## Cron System

Cron jobs schedule recurring AI agent tasks without any external trigger. Each job runs on a Spring cron expression and routes a configurable prompt to an agent.

### How It Works

```
CronScheduler (Spring CronTrigger)
         ↓  fires on schedule
     EventQueue
         ↓
  AgentDispatcher (async)
         ↓
  prompt = template.replace({{cronName}}, {{timestamp}})
         ↓
   AgentSession.chatWithAgent()
         ↓
  CronOutputService → LOG / REPLY_URL / NONE
```

### Cron Management API

```bash
# Register a cron job
POST /api/cron-definitions
{
  "name": "daily-summary",
  "description": "Runs every day at 9am on weekdays",
  "cronExpression": "0 0 9 * * MON-FRI",
  "promptTemplate": "Generate a brief status summary. Timestamp: {{timestamp}}",
  "agentName": "default",
  "outputTarget": "LOG"
}
# → returns { "id": "abc-123", ... }

# List all cron jobs
GET /api/cron-definitions

# Get one
GET /api/cron-definitions/{id}

# Enable / disable (without deleting)
PUT /api/cron-definitions/{id}/enable
PUT /api/cron-definitions/{id}/disable

# Delete
DELETE /api/cron-definitions/{id}
```

### Prompt Template Placeholders

| Placeholder | Value |
|---|---|
| `{{cronName}}` | The job's `name` field |
| `{{timestamp}}` | ISO-8601 timestamp when the job fired |

### Output Targets

| Target | Behaviour |
|---|---|
| `LOG` | Agent response written to application log (default) |
| `REPLY_URL` | Agent response HTTP POSTed as JSON to `replyUrl` |
| `NONE` | Response discarded (fire-and-forget) |

### Cron Expression Format

6-field Spring format: `second minute hour day-of-month month day-of-week`

| Expression | Meaning |
|---|---|
| `*/10 * * * * *` | Every 10 seconds |
| `0 * * * * *` | Every minute |
| `0 0 * * * *` | Every hour |
| `0 0 9 * * MON-FRI` | 9am on weekdays |

### Session Isolation

Each cron job gets a dedicated session `cron:{id}` by default, giving it its own persistent conversation memory. Set `sessionId` on the definition to share context with another session.

---

## Hook System

Hooks are lightweight lifecycle callbacks fired at key points in the gateway. They enable extensible automation without modifying core code.

### Hook Events

| Event | Fired When |
|---|---|
| `GATEWAY_STARTUP` | Application is fully started |
| `AGENT_BOOTSTRAP` | An agent instance is created for the first time in a session |
| `SESSION_START` | A new session is created |
| `SESSION_END` | A session ends |
| `COMMAND_NEW` | User sends `/new` |
| `COMMAND_RESET` | User sends `/reset` |
| `COMMAND_STOP` | User sends `/stop` |
| `TOOL_BEFORE` | Before an agent tool call |
| `TOOL_AFTER` | After an agent tool call |
| `LLM_INPUT` | Before sending a prompt to the LLM |
| `LLM_OUTPUT` | After receiving a response from the LLM |

### Bundled Hooks

| Hook | Event | Default | Description |
|---|---|---|---|
| `session-memory` | `COMMAND_NEW` | **Enabled** | Saves a Markdown snapshot to `~/.openclaw4j/memory/` |
| `command-logger` | `COMMAND_NEW/RESET/STOP` | Disabled | Appends command log to `~/.openclaw4j/command-log.txt` |
| `bootstrap-extra-files` | `AGENT_BOOTSTRAP` | Disabled | Injects files from `./hooks/bootstrap-extra-files/` into agent context |

### Hook Management API

```bash
# List all hooks and their enabled status
GET /api/hooks

# Enable a hook
PUT /api/hooks/{name}/enable

# Disable a hook
PUT /api/hooks/{name}/disable
```

---

## Running the Gateway

### 1. Build

```bash
mvn install -DskipTests
```

### 2. Configure

```bash
# OpenAI (default)
export OPENAI_API_KEY=sk-...

# Or Ollama — also set app.ai.local: true in gateway/src/main/resources/application.yaml
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.3

# MS Teams (optional)
export MICROSOFT_APP_ID=...
export MICROSOFT_APP_PASSWORD=...
export MICROSOFT_TENANT_ID=...
```

### 3. Run

```bash
mvn spring-boot:run -pl gateway
# or
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

Server starts on **port 8881**.

### Environment Variables

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | Yes (OpenAI mode) | OpenAI API key |
| `OPENAI_MODEL` | No | Model (default: `gpt-4o-mini`) |
| `OPENAI_TIMEOUT` | No | Request timeout (default: `60s`) |
| `OLLAMA_BASE_URL` | No | Ollama URL (default: `http://localhost:11434`) |
| `OLLAMA_MODEL` | No | Ollama model (default: `llama3.3`) |
| `TAVILY_API_KEY` | No | Tavily web search key |
| `MICROSOFT_APP_ID` | No | Bot Framework app ID |
| `MICROSOFT_APP_PASSWORD` | No | Bot Framework app password |
| `MICROSOFT_TENANT_ID` | No | Azure AD tenant ID |
