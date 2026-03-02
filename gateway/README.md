# Gateway Module

## Overview
The `gateway` module acts as a message routing gateway between external messaging clients and AI agents. It follows the Gateway Pattern to centralize communication, providing a single point of entry and managing cross-cutting concerns.

## Gateway Pattern
The Gateway Pattern is used to provide a unified interface to a set of internal services or agents. In this implementation, the gateway handles:
- **Input Adapters**: Entry points for various external communication protocols (e.g., REST, WebSocket).
- **Event Queue**: Decouples incoming messages from processing, allowing for asynchronous handling and scaling.
- **Agent Dispatcher**: Routes messages to the appropriate AI agents based on intent, context, or session data.
- **Session Management**: Maintains state and context for ongoing conversations between clients and agents.
- **Memory Store**: Provides persistence for conversation history and agent state.
- **Channel Integrations**: Specific connectors for different messaging platforms (e.g., Slack, Discord, MS Teams).

## Key Features
- **Virtual Threads**: Enabled to handle a high volume of concurrent connections with minimal overhead.
- **Spring Boot**: Utilizes Spring Web for RESTful APIs, Spring Scheduling for background tasks, and Spring Events for internal decoupled communication.
- **WebClient**: Used for non-blocking outbound requests to agents or external APIs.

## Package Structure
- `adapter.input`: Controllers and listeners for external requests.
- `eventqueue`: Components for internal message buffering and queuing.
- `dispatcher`: Logic for routing messages to agents.
- `session`: Management of client-agent conversation sessions.
- `memory`: Data access for conversation and agent memory.
- `integration`: Specific integration logic for external messaging channels.

## Configuration
Virtual threads are enabled via:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```
