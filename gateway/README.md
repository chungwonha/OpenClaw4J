# Gateway Module

## Overview

The `gateway` module is an event-driven HTTP server (port 8881) that routes messages from external systems to AI agents and sends responses back. It follows the Gateway Pattern — all inputs are normalized into `GatewayEvent`s, queued in-memory, and dispatched asynchronously so the HTTP layer is never blocked by AI processing.

## Architecture

```
Input Adapters          EventQueue          AgentDispatcher (every 100ms)
─────────────           ──────────          ─────────────────────────────
TeamsController    →                   →    handleMessage  → TeamsReplyService
WebhookController  →    GatewayEvent   →    handleWebhook  → WebhookOutputService
HeartbeatScheduler →                   →    handleHeartbeat
```

### Key Components

- **Input Adapters** (`adapter.input`): Accept external requests, enqueue a `GatewayEvent`, return immediately.
- **EventQueue**: In-memory `LinkedBlockingQueue` that decouples input from processing.
- **AgentDispatcher**: Drains the queue every 100ms and fans out each event to a virtual thread.
- **SessionRegistry**: Creates and manages `AgentSession`s keyed by conversation ID.
- **AgentSession**: One session per conversation. Routes messages to lazily-created `ChatAgent` instances, each with an isolated 200-message memory window.
- **AgentRegistry**: Stores named agent definitions (`AgentDefinition`). The `"default"` agent is always present.
- **WebhookRegistry**: Stores registered webhook definitions at runtime.

## Input Channels

### MS Teams (`POST /api/messages`)

Receives Microsoft Bot Framework Activity v3 events. Supports `message` and `conversationUpdate` activity types. JWT token verification is configurable via `gateway.teams.verify-token`.

### Webhooks (`POST /api/webhooks/{id}`)

External systems trigger agent tasks by POSTing any payload to a registered webhook URL. See [Webhook System](#webhook-system) below.

## In-Chat Commands

Works across all input channels:

| Command | Description |
|---|---|
| `/new` | Clear all agent instances in the session and start fresh |
| `/reset` | Clear the current agent's memory only |
| `/stop` | Stop the session (fires COMMAND_STOP hook) |
| `/use <agentName>` | Permanently switch the session's active agent |
| `/agents` | List all registered agents and the current active one |
| `@<agentName> <message>` | One-shot message to a named agent without switching |

## Agent Management API

```
GET    /api/agents          — list all registered agents
GET    /api/agents/{name}   — get a specific agent
POST   /api/agents          — register/overwrite an agent { "name": "...", "description": "..." }
DELETE /api/agents/{name}   — remove an agent (cannot remove "default")
```

## Webhook System

Webhooks enable event-driven AI automation. External systems (CI pipelines, monitoring, Slack, etc.) POST payloads to OpenClaw4J, which converts them into agent tasks and optionally delivers the result.

### Webhook Management API

```
GET    /api/webhook-definitions          — list all webhooks
GET    /api/webhook-definitions/{id}     — get a specific webhook
POST   /api/webhook-definitions          — register a webhook
DELETE /api/webhook-definitions/{id}     — remove a webhook
```

**Register request body:**
```json
{
  "name": "ci-failure-alert",
  "description": "GitHub Actions failure handler",
  "agentName": "default",
  "sessionId": null,
  "promptTemplate": "A CI build failed. Summarize and suggest a fix:\n{{payload}}",
  "outputTarget": "LOG",
  "replyUrl": null
}
```

| Field | Required | Default | Description |
|---|---|---|---|
| `name` | Yes | — | Human-readable name |
| `description` | No | `""` | What this webhook does |
| `agentName` | No | `"default"` | Which registered agent handles it |
| `sessionId` | No | `null` | Pin to a specific session; `null` = dedicated `webhook:{id}` session |
| `promptTemplate` | Yes | — | Agent prompt with `{{payload}}`, `{{webhookName}}`, `{{timestamp}}` placeholders |
| `outputTarget` | No | `LOG` | `NONE` / `LOG` / `REPLY_URL` |
| `replyUrl` | Conditional | — | Required when `outputTarget=REPLY_URL` |

### Trigger a Webhook

```bash
POST /api/webhooks/{id}
Content-Type: application/json   # any content-type accepted

{ "repo": "my-app", "status": "failed", "step": "unit-tests" }
```

Returns `202 Accepted`. The agent processes the payload asynchronously.

### Output Targets

| Target | Behaviour |
|---|---|
| `LOG` | Agent response written to application log at INFO level |
| `REPLY_URL` | Agent response POSTed as JSON `{webhookId, webhookName, result}` to `replyUrl` |
| `NONE` | Response discarded (fire-and-forget) |

## Hook System

Hooks are lifecycle callbacks fired at key gateway events. They enable extensible automation without modifying core code.

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
| `session-memory` | `COMMAND_NEW` | **Enabled** | Saves a Markdown snapshot to `~/.openclaw4j/memory/<sessionId>-<timestamp>.md` |
| `command-logger` | `COMMAND_NEW/RESET/STOP` | Disabled | Appends command log to `~/.openclaw4j/command-log.txt` |
| `bootstrap-extra-files` | `AGENT_BOOTSTRAP` | Disabled | Injects files from `./hooks/bootstrap-extra-files/` into agent context |

### Hook Management API

```
GET /api/hooks                   — list all hooks and their enabled status
PUT /api/hooks/{name}/enable     — enable a hook
PUT /api/hooks/{name}/disable    — disable a hook
```

## Package Structure

| Package | Contents |
|---|---|
| `adapter.input` | `TeamsController`, `WebhookController`, `WebhookManagementController`, `AgentManagementController` |
| `agent` | `AgentRegistry`, `AgentDefinition` |
| `session` | `SessionRegistry`, `AgentSession` |
| `dispatcher` | `AgentDispatcher` |
| `eventqueue` | `EventQueue`, `GatewayEvent`, `GatewayEventType` |
| `webhook` | `WebhookRegistry`, `WebhookDefinition`, `WebhookContext`, `WebhookOutputService` |
| `hook` | `HookRegistry`, `HookExecutor`, `HookDefinition`, `HookEventType`, `HookContext`, `HookManagementController`, bundled hooks |
| `integration.teams` | `TeamsActivity`, `TeamsReplyService`, `TeamsTokenService` |
| `scheduler` | `HeartbeatScheduler` |
| `config` | `GatewayAiConfig`, `CoreAgentConfig` |

## Prerequisites

- Java 17+
- Maven 3.8+
- OpenAI API key **or** a running Ollama instance

## Environment Variables

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

## Running

```bash
# 1. Build from project root
mvn install -DskipTests

# 2. Set env vars
export OPENAI_API_KEY=sk-...

# 3. Run
cd gateway && mvn spring-boot:run
# or
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

Server starts on **port 8881**.

### Ollama (local inference)

Set `app.ai.local: true` in `src/main/resources/application.yaml` and provide:

```bash
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.3
```

## Key `application.yaml` Settings

```yaml
server:
  port: 8881

app:
  ai:
    local: false          # true = Ollama, false = OpenAI

gateway:
  heartbeat:
    enabled: true
    interval-ms: 1800000  # 30 minutes
  teams:
    verify-token: true    # enable JWT verification for production

spring:
  threads:
    virtual:
      enabled: true
```
