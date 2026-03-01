# MyCalw2 - AI Chat Bot

MyCalw2 is a Spring Boot application that provides an interactive AI Chat Bot powered by [LangChain4j](https://github.com/langchain4j/langchain4j). It supports both OpenAI and local AI models via Ollama. The bot is equipped with several tools that allow it to interact with the operating system, file system, and perform HTTP requests, making it a powerful assistant.

## Features

- **Multi-Agent Support:** Run multiple independent chat sessions (agents) simultaneously and switch between them.
- **AI Model Options:** Use OpenAI's GPT-4o-mini (default) or a local model via [Ollama](https://ollama.com/).
- **Integrated Tools:**
    - **CommandLineTool:** Allows the AI to execute OS commands (stdout and stderr are captured).
    - **FileManagementTool:** Enables the AI to read, write, append, delete, and list files and directories.
    - **HttpRequestTool:** Allows the AI to perform HTTP GET, POST, PUT, and DELETE requests.
- **Web Search:** Integrated with Tavily for web searching capabilities (if configured).

## Prerequisites

- **Java 21:** This application requires JDK 21.
- **Maven:** The project uses Maven (wrapper provided as `./mvnw`).
- **Ollama (Optional):** Required for running models locally.
- **OpenAI API Key (Optional):** Required for using OpenAI models.
- **Tavily API Key (Optional):** Required for web search functionality.

## Setup & Configuration

Configuration is managed via `src/main/resources/application.yaml` and environment variables.

### Key Configuration Properties

| Property | Environment Variable | Default Value | Description |
|---|---|---|---|
| `app.ai.local` | - | `true` | Set to `true` to use Ollama, `false` to use OpenAI. |
| `app.ai.ollama.model` | `OLLAMA_MODEL` | `llama3.3` | The model name to use with Ollama. |
| `app.ai.ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` | The base URL for the Ollama server. |
| `openai.api-key` | `OPENAI_API_KEY` | `dummy` | Your OpenAI API Key. |
| `openai.model` | `OPENAI_MODEL` | `gpt-4o-mini` | The OpenAI model to use. |
| `TAVILY_API_KEY` | `TAVILY_API_KEY` | `dummy` | Your Tavily API Key for web searches. |

### Local Setup (Ollama)

1. [Download and install Ollama](https://ollama.com/).
2. Pull the desired model (e.g., `ollama pull llama3.3`).
3. Ensure `app.ai.local` is set to `true` in `application.yaml`.

### OpenAI Setup

1. Obtain an API key from [OpenAI](https://platform.openai.com/).
2. Set the `OPENAI_API_KEY` environment variable or update `application.yaml`.
3. Set `app.ai.local` to `false`.

## Running the Application

You can run the application using the Maven wrapper:

```bash
./mvnw spring-boot:run
```

## How to Use

Once started, the application launches an interactive command-line interface:

- **Type your message:** Just type and press Enter to chat with the active agent.
- **`/agents`**: Create a new independent chat agent.
- **`/list`**: List all current agents and see which one is active.
- **`/use <index>`**: Switch to a specific agent (e.g., `/use 1`).
- **`exit` or `quit`**: Exit the application.

## Development

The project includes several tests for its tools:
- `CommandLineToolTest.java`
- `FileManagementToolTest.java`

Run tests using:
```bash
./mvnw test
```
