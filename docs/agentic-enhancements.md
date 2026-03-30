# Agentic Enhancements — Feature Guide

Branch: `feature/agentic-enhancements`

Five features were added to increase the autonomous and observable capability of the gateway. Each section describes what changed, how it works, and how to test it.

---

## Table of Contents

1. [Pipeline Observability Hooks](#1-pipeline-observability-hooks)
2. [Proactive Heartbeat](#2-proactive-heartbeat)
3. [Agent-to-Agent Delegation](#3-agent-to-agent-delegation)
4. [Persistent Registries](#4-persistent-registries)
5. [Bootstrap Context Injection](#5-bootstrap-context-injection)

---

## 1. Pipeline Observability Hooks

### What changed

Every tool invocation and every LLM call now fires a lifecycle hook event. Previously, `TOOL_BEFORE`, `TOOL_AFTER`, `LLM_INPUT`, and `LLM_OUTPUT` were defined in `HookEventType` but never fired.

**New files:**
- `core/.../tool/ToolExecutionListener.java` — interface with `beforeToolExecution` / `afterToolExecution`
- `gateway/.../pipeline/GatewayToolExecutionListener.java` — Spring component that implements the interface and calls `HookExecutor`

**Modified files:**
- `CompositeToolProvider` — wraps every `ToolExecutor` (static and MCP) to fire listener callbacks before and after execution
- `ChatAgentFactory` — accepts a `ToolExecutionListener` via `setToolExecutionListener()`; stamps session ID onto each provider
- `CoreAgentConfig` — wires `GatewayToolExecutionListener` into `ChatAgentFactory`
- `AgentSession.routeTo()` — fires `LLM_INPUT` before `agent.chat()` and `LLM_OUTPUT` after

### Hook event reference

| Event | When fired | Attributes in context |
|---|---|---|
| `TOOL_BEFORE` | Immediately before any tool method executes | `toolName`, `toolInput` |
| `TOOL_AFTER` | Immediately after any tool method returns | `toolName`, `toolInput`, `toolOutput` |
| `LLM_INPUT` | Before the prompt is sent to the LLM | `agentName` |
| `LLM_OUTPUT` | After the LLM response is received | `agentName` |

### How to test

**Enable the command logger hook** to see tool events logged to a file:

```bash
# Enable the bundled command logger
curl -X PUT http://localhost:8881/api/hooks/command-logger-new/enable
curl -X PUT http://localhost:8881/api/hooks/command-logger-reset/enable
curl -X PUT http://localhost:8881/api/hooks/command-logger-stop/enable
```

**Write a custom hook** to verify tool events are firing. Register one at startup by adding a `@PostConstruct` bean, or check `DEBUG` logs:

```
# In application.yaml, set logging level to DEBUG for the pipeline package:
logging:
  level:
    com.chung.ai.software.openclaw4j.gateway.pipeline: DEBUG
    com.chung.ai.software.openclaw4j.gateway.session: DEBUG
```

Send a message that triggers a tool (e.g., "list the files in the current directory") and observe the log output:

```
[Pipeline] TOOL_BEFORE tool='listFiles' session='conv-123:default'
[Pipeline] TOOL_AFTER  tool='listFiles' session='conv-123:default'
[Session]  LLM_INPUT   session='conv-123'
[Session]  LLM_OUTPUT  session='conv-123'
```

---

## 2. Proactive Heartbeat

### What changed

The heartbeat event (fires every 30 minutes by default) now runs configurable prompts against active sessions. Previously `handleHeartbeat()` only logged the session count.

**New files:**
- `gateway/.../heartbeat/HeartbeatDefinition.java` — definition model
- `gateway/.../heartbeat/HeartbeatRegistry.java` — in-memory registry
- `gateway/.../heartbeat/HeartbeatOutputService.java` — NONE / LOG / REPLY_URL delivery
- `gateway/.../adapter/input/HeartbeatManagementController.java` — REST CRUD at `/api/heartbeat-definitions`

**Modified files:**
- `AgentDispatcher.handleHeartbeat()` — iterates `HeartbeatRegistry`, fans out to matching sessions, runs prompts, delivers results

### REST API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/heartbeat-definitions` | List all heartbeat definitions |
| `GET` | `/api/heartbeat-definitions/{id}` | Get one by ID |
| `POST` | `/api/heartbeat-definitions` | Create a new definition |
| `DELETE` | `/api/heartbeat-definitions/{id}` | Remove a definition |
| `PUT` | `/api/heartbeat-definitions/{id}/enable` | Enable |
| `PUT` | `/api/heartbeat-definitions/{id}/disable` | Disable |

### Request body for POST

```json
{
  "name": "status-check",
  "description": "Ask the default agent for a brief status update every heartbeat",
  "agentName": "default",
  "sessionFilter": "all",
  "promptTemplate": "This is a scheduled proactive check. Timestamp: {{timestamp}}. Session: {{sessionId}}. Briefly acknowledge you are active and report any pending items if known.",
  "outputTarget": "LOG",
  "replyUrl": null,
  "enabled": true
}
```

**Fields:**

| Field | Default | Description |
|---|---|---|
| `name` | required | Human-readable identifier |
| `agentName` | `"default"` | Which agent handles the prompt |
| `sessionFilter` | `"all"` | `"all"` or an exact `conversationId` |
| `promptTemplate` | built-in | Supports `{{sessionId}}` and `{{timestamp}}` |
| `outputTarget` | `LOG` | `NONE`, `LOG`, or `REPLY_URL` |
| `replyUrl` | null | Required when `outputTarget` is `REPLY_URL` |
| `enabled` | `true` | Whether this fires on heartbeat |

### How to test

**Option A — Reduce heartbeat interval for testing:**

In `gateway/src/main/resources/application.yaml`, temporarily set:
```yaml
gateway:
  heartbeat:
    enabled: true
    interval-ms: 60000   # 1 minute instead of 30
```

**Option B — Trigger the heartbeat scheduler directly** (requires a test endpoint or Spring Actuator).

**Step-by-step test:**

1. Start the gateway and send one Teams message to create an active session.

2. Register a heartbeat definition:
```bash
curl -s -X POST http://localhost:8881/api/heartbeat-definitions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-check",
    "agentName": "default",
    "sessionFilter": "all",
    "promptTemplate": "Proactive check. Time: {{timestamp}}. Please reply with OK and the session ID: {{sessionId}}",
    "outputTarget": "LOG"
  }'
```

3. Wait for the next heartbeat tick (check logs for `[Dispatcher] Heartbeat —`).

4. Observe log output:
```
[Dispatcher] Heartbeat — 1 active session(s)
[Dispatcher] Heartbeat='test-check' running for session='<conversationId>'
```

5. With `outputTarget: REPLY_URL`, the result POSTs JSON to `replyUrl`:
```json
{ "heartbeatId": "...", "heartbeatName": "test-check", "sessionId": "...", "result": "OK, session is ..." }
```

---

## 3. Agent-to-Agent Delegation

### What changed

Users and automated flows can now delegate tasks from one agent to another within the same session. The `AGENT_MESSAGE` event type (previously unhandled) now has a full dispatcher handler.

**New files:**
- `gateway/.../agent/AgentMessageContext.java` — carries `targetAgentName`, `fromAgentName`, `replySessionId`

**Modified files:**
- `AgentSession.chat()` — added `/delegate @agentName task` routing rule and `handleDelegateCommand()`
- `AgentDispatcher` — added `AGENT_MESSAGE` case and `handleAgentMessage()` handler

### In-chat command

```
/delegate @<agentName> <task description>
```

Routes the task to the named agent and returns its response, prefixed with `[from @agentName]:`.

**Example:**
```
User:    /delegate @researcher What are the key differences between RAG and fine-tuning?
Bot:     [from @researcher]: RAG retrieves relevant context at inference time...
```

The active agent does not change. Delegation is a one-shot call.

### AGENT_MESSAGE event (programmatic)

Enqueue an `AGENT_MESSAGE` event via the `EventQueue` to delegate tasks between sessions programmatically:

```java
// Example: agent in session A delegates to agent in session B
eventQueue.enqueue(GatewayEvent.builder()
    .type(GatewayEventType.AGENT_MESSAGE)
    .sessionId("session-B")           // session where the target agent lives
    .payload("Summarize these logs: ...")
    .metadata(AgentMessageContext.builder()
        .targetAgentName("summarizer")
        .fromAgentName("monitor")
        .replySessionId("session-A")  // deliver result back to session A
        .build())
    .timestamp(Instant.now())
    .build());
```

When `replySessionId` is set and differs from `sessionId`, the result is also delivered back to the originating session's agent.

### How to test

**Prerequisites:** Register a second agent.

```bash
curl -s -X POST http://localhost:8881/api/agents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "researcher",
    "description": "You are a research specialist. Answer questions with depth and citations where possible."
  }'
```

**Test via Teams or curl:**

```bash
# Simulate a Teams message with the /delegate command
curl -s -X POST http://localhost:8881/api/messages \
  -H "Content-Type: application/json" \
  -d '{
    "type": "message",
    "id": "test-001",
    "timestamp": "2026-03-29T00:00:00Z",
    "serviceUrl": "http://localhost:8881",
    "channelId": "test",
    "from": { "id": "user1", "name": "Test User" },
    "conversation": { "id": "test-session-1" },
    "recipient": { "id": "bot", "name": "Bot" },
    "text": "/delegate @researcher Explain transformer attention in simple terms",
    "replyToId": "test-001"
  }'
```

Check logs for:
```
[Session] 'test-session-1' delegating task to agent='researcher': Explain transformer...
[from @researcher]: The attention mechanism works by...
```

**Test error case** (agent not registered):
```
/delegate @nonexistent do something
→ ❌ Agent nonexistent is not found.
```

---

## 4. Persistent Registries

### What changed

`AgentRegistry`, `CronRegistry`, and `WebhookRegistry` now persist their definitions to disk. Previously all registered agents, crons, and webhooks were lost on gateway restart.

**New files:**
- `gateway/.../persistence/RegistryPersistenceService.java` — writes / reads / deletes JSON files under `~/.openclaw4j/registry/{type}/{id}.json`

**Modified files:**
- `AgentDefinition`, `CronDefinition`, `WebhookDefinition` — added `@JsonIgnoreProperties(ignoreUnknown=true)`, `@NoArgsConstructor`, `@AllArgsConstructor` for Jackson compatibility
- `AgentRegistry` — `@PostConstruct` reloads saved definitions; `register()` / `unregister()` persist immediately
- `CronRegistry` — same, plus `@Lazy CronScheduler` re-schedules all loaded cron jobs on startup
- `WebhookRegistry` — same as `AgentRegistry`

### Storage layout

```
~/.openclaw4j/
└── registry/
    ├── agents/
    │   └── researcher.json
    ├── crons/
    │   └── <uuid>.json
    └── webhooks/
        └── <uuid>.json
```

Each file is self-contained JSON — one file per definition. The `default` agent is never written to disk (it is always created in code).

### How to test

1. Register a custom agent, cron, and webhook:

```bash
# Agent
curl -s -X POST http://localhost:8881/api/agents \
  -H "Content-Type: application/json" \
  -d '{ "name": "persist-test", "description": "Test persistence" }'

# Cron
curl -s -X POST http://localhost:8881/api/cron-definitions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-cron",
    "agentName": "default",
    "cronExpression": "0 * * * * *",
    "promptTemplate": "Cron fired: {{cronName}} at {{timestamp}}",
    "outputTarget": "LOG"
  }'

# Webhook
curl -s -X POST http://localhost:8881/api/webhook-definitions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-webhook",
    "agentName": "default",
    "promptTemplate": "Webhook received: {{payload}}",
    "outputTarget": "LOG"
  }'
```

2. Verify the files were created:

```bash
ls ~/.openclaw4j/registry/agents/
ls ~/.openclaw4j/registry/crons/
ls ~/.openclaw4j/registry/webhooks/
```

3. Restart the gateway.

4. Verify they were reloaded:

```bash
curl -s http://localhost:8881/api/agents | jq '.[].name'
curl -s http://localhost:8881/api/cron-definitions | jq '.[].name'
curl -s http://localhost:8881/api/webhook-definitions | jq '.[].name'
```

All three should list the registered names. Cron jobs should also resume their schedules (check logs for scheduler output).

5. Delete one and verify the file is removed:

```bash
curl -X DELETE http://localhost:8881/api/agents/persist-test
ls ~/.openclaw4j/registry/agents/   # persist-test.json should be gone
```

---

## 5. Bootstrap Context Injection

### What changed

The `bootstrap-extra-files` hook now reads the file contents from `./hooks/bootstrap-extra-files/` and injects them into the agent's system description when the agent is first created. Previously the hook discovered the files but only set a result string without using it.

**Modified files:**
- `BootstrapExtraFilesHook` — reads each `.md`/`.txt` file with `Files.readString()` and sets `context.result` to the combined content under a `--- Bootstrap Context ---` header
- `AgentSession.routeTo()` — reads `bootstrapContext.getResult()` after firing `AGENT_BOOTSTRAP` and appends it to the agent description before calling `agentFactory.createChatAgent()`

### How it works

```
Agent first used in a session
         ↓
AGENT_BOOTSTRAP hook fires
         ↓
BootstrapExtraFilesHook (if enabled) reads ./hooks/bootstrap-extra-files/*.md|.txt
         ↓
Combined content set as context.result
         ↓
AgentSession appends it to the agent description (system prompt context)
         ↓
ChatAgentFactory creates the agent with the enriched description
```

The hook is **disabled by default**. Enable it via the hooks API, then all subsequent agent bootstrap events in any session will inject the file contents.

### How to test

1. Create the bootstrap directory and a sample file:

```bash
mkdir -p hooks/bootstrap-extra-files

cat > hooks/bootstrap-extra-files/persona.md << 'EOF'
You are an expert in cloud infrastructure and DevOps.
Always recommend infrastructure-as-code approaches.
When answering questions about deployments, mention observability and rollback strategies.
EOF
```

2. Enable the hook:

```bash
curl -X PUT http://localhost:8881/api/hooks/bootstrap-extra-files/enable
```

3. Start a new session (or `/new` in an existing one to clear agents) and ask a question:

```bash
curl -s -X POST http://localhost:8881/api/messages \
  -H "Content-Type: application/json" \
  -d '{
    "type": "message",
    "id": "bs-test-001",
    "timestamp": "2026-03-29T00:00:00Z",
    "serviceUrl": "http://localhost:8881",
    "channelId": "test",
    "from": { "id": "user1", "name": "Tester" },
    "conversation": { "id": "bootstrap-test-session" },
    "recipient": { "id": "bot", "name": "Bot" },
    "text": "How should I deploy a new microservice?",
    "replyToId": "bs-test-001"
  }'
```

4. The response should reflect the persona context from `persona.md` (mentions IaC, observability, rollback).

5. Check logs for bootstrap injection:

```
[BootstrapExtraFilesHook] Found extra file: persona.md
[Session] Creating instance of agent='default' for session='bootstrap-test-session'
```

6. Add multiple files — all will be concatenated and injected in alphabetical order.

**To disable injection without removing files:**

```bash
curl -X PUT http://localhost:8881/api/hooks/bootstrap-extra-files/disable
```

---

## Complete New REST Endpoints Summary

| Method | Path | Feature |
|---|---|---|
| `GET` | `/api/heartbeat-definitions` | Proactive Heartbeat |
| `GET` | `/api/heartbeat-definitions/{id}` | Proactive Heartbeat |
| `POST` | `/api/heartbeat-definitions` | Proactive Heartbeat |
| `DELETE` | `/api/heartbeat-definitions/{id}` | Proactive Heartbeat |
| `PUT` | `/api/heartbeat-definitions/{id}/enable` | Proactive Heartbeat |
| `PUT` | `/api/heartbeat-definitions/{id}/disable` | Proactive Heartbeat |

All previously existing endpoints (`/api/agents`, `/api/cron-definitions`, `/api/webhook-definitions`, `/api/hooks`, `/api/webhooks/{id}`) are unchanged in their API contract.

---

## Logging Reference

Set these logger levels for detailed diagnostic output:

```yaml
logging:
  level:
    com.chung.ai.software.openclaw4j.gateway.pipeline: DEBUG   # TOOL_BEFORE/AFTER
    com.chung.ai.software.openclaw4j.gateway.session: DEBUG    # LLM_INPUT/OUTPUT + delegation
    com.chung.ai.software.openclaw4j.gateway.persistence: DEBUG # registry save/load/delete
    com.chung.ai.software.openclaw4j.gateway.heartbeat: DEBUG  # heartbeat delivery
    com.chung.ai.software.openclaw4j.gateway.hook: DEBUG       # all hook firing
```
