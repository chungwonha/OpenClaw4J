package com.chung.ai.software.mycalw2.gateway.hook;

/**
 * Enumeration of all lifecycle events that the hook system recognises.
 *
 * Each value carries a human-readable description used in management APIs
 * and log output so operators know what each event type represents without
 * having to read source code.
 */
public enum HookEventType {

    GATEWAY_STARTUP("Fired once when the Spring application context is fully ready"),
    AGENT_BOOTSTRAP("Fired the first time a named agent instance is created inside a session"),
    SESSION_START("Fired when a new AgentSession is constructed"),
    SESSION_END("Fired when a session is torn down or explicitly stopped"),
    COMMAND_NEW("Fired when the user issues the /new command"),
    COMMAND_RESET("Fired when the user issues the /reset command"),
    COMMAND_STOP("Fired when the user issues the /stop command"),
    TOOL_BEFORE("Fired immediately before a LangChain4j tool is invoked"),
    TOOL_AFTER("Fired immediately after a LangChain4j tool completes"),
    LLM_INPUT("Fired with the prompt text just before it is sent to the LLM"),
    LLM_OUTPUT("Fired with the raw LLM response text after it is received");

    private final String description;

    HookEventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
