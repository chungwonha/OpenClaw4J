# Gateway Module

## Overview
The `gateway` module acts as a message routing gateway between external messaging clients and AI agents. It follows the Gateway Pattern to centralize communication, providing a single point of entry and managing cross-cutting concerns.

## Gateway Pattern & Multi-Agent Routing
The gateway provides a unified interface to a set of internal AI agents. Key components include:
- **Input Adapters**: Entry points for various external communication protocols (e.g., REST, MS Teams).
- **Session Registry**: Manages active `AgentSession`s, keyed by conversation ID.
- **Agent Registry**: Central repository for named agent definitions (e.g., "default", "research").
- **Agent Session**: Each session maintains its own isolated agent instances and a 200-message memory window.
- **Dynamic Routing**: Messages are routed to agents based on the session's active agent or specific in-chat commands.

## In-Chat Commands
Users can manage agent interactions directly within their messaging client:
- `/use <agentName>` — Permanently switch the session's active agent.
- `/agents` — List all registered agents and the current active one.
- `@<agentName> <message>` — Send a one-shot message to a specific agent without switching.
- `<plain text>` — All other messages are routed to the session's current active agent.

## Key Features
- **Virtual Threads**: Enabled to handle a high volume of concurrent connections with minimal overhead.
- **Spring Boot**: Utilizes Spring Web for RESTful APIs and Spring Beans for modular component management.
- **Isolated Memory**: Each agent instance within a session has its own memory, ensuring no leakage between different conversations or agents.
- **Lazy Agent Creation**: Agents are instantiated on-demand when first addressed in a session.

## Package Structure
- `adapter.input`: Controllers and listeners for external requests (e.g., `TeamsController`).
- `agent`: Core registry and definitions for available AI agents (`AgentRegistry`, `AgentDefinition`).
- `session`: Management of client-agent conversation sessions and routing logic (`SessionRegistry`, `AgentSession`).
- `config`: Spring configuration for the gateway module.

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- One of:
  - An **OpenAI API key** (default), or
  - A running **Ollama** instance (for local inference)

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | Yes (if using OpenAI) | Your OpenAI API key |
| `OPENAI_MODEL` | No | Model name (default: `gpt-4o-mini`) |
| `OPENAI_TIMEOUT` | No | Request timeout (default: `60s`) |
| `POSTGRES_PASSWORD` | No | Password for PostgreSQL (if persistence is enabled) |
| `TAVILY_API_KEY` | No | Tavily search API key (enables web search tool) |
| `OLLAMA_BASE_URL` | No | Ollama server URL (default: `http://localhost:11434`) |
| `OLLAMA_MODEL_NAME` | No | Ollama model name alias |
| `OLLAMA_MODEL` | No | Ollama model to use (default: `llama3.3`) |

For MS Teams integration (optional):

| Variable | Required | Description |
|---|---|---|
| `MICROSOFT_APP_ID` | No | Bot Framework app ID |
| `MICROSOFT_APP_PASSWORD` | No | Bot Framework app password |

## Running the Gateway

### 1. Build the parent project first

From the project root:

```bash
mvn install -DskipTests
```

### 2. Set environment variables

Create a `.env` file or export variables in your shell:

```bash
# Minimal — OpenAI mode
export OPENAI_API_KEY=sk-...

# Optional overrides
export OPENAI_MODEL=gpt-4o-mini
export OPENAI_TIMEOUT=60s

# For local Ollama mode, also set app.ai.local=true in application.yaml
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.3
```

### 3. Run the gateway module

```bash
cd gateway
mvn spring-boot:run
```

Or run the fat jar after building:

```bash
java -jar target/gateway-0.0.1-SNAPSHOT.jar
```

The server starts on **port 8881** by default.

### Using Ollama (local inference)

Set `app.ai.local: true` in `src/main/resources/application.yaml`, then provide Ollama env vars:

```bash
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.3
```

No `OPENAI_API_KEY` is needed in this mode.

## Configuration

Key settings in `application.yaml`:

```yaml
server:
  port: 8881

app:
  ai:
    local: false          # set true to use Ollama instead of OpenAI

openai:
  api-key: ${OPENAI_API_KEY:}
  model: gpt-4o-mini
  timeout: 60s

gateway:
  heartbeat:
    enabled: true
    interval-ms: 1800000  # 30 minutes
  teams:
    verify-token: false   # enable JWT verification for production
```

Virtual threads are enabled via:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```
