package com.chung.ai.software.openclaw4j.gateway.hook;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Metadata and handler for a single registered hook.
 *
 * The {@link HookHandler} is deliberately excluded from {@code toString} and
 * {@code equals}/{@code hashCode} because lambdas do not produce meaningful
 * output for those operations and should not affect equality comparisons used
 * internally by the registry.
 *
 * The {@code enabled} flag is {@code volatile} so that enable/disable calls
 * from management endpoints are immediately visible to the executor threads
 * that read it without requiring synchronisation.
 */
@Data
@Builder
public class HookDefinition {

    /** Unique identifier used as the registry key. */
    private String name;

    /** Human-readable description surfaced by management APIs. */
    private String description;

    /** The lifecycle event this definition is registered for. */
    private HookEventType eventType;

    /** The callback to invoke when the event fires. */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private HookHandler handler;

    /** Whether this hook will be invoked by the executor. */
    private volatile boolean enabled;

    // ------------------------------------------------------------------ //
    //  Serialisation projection
    // ------------------------------------------------------------------ //

    /**
     * Lightweight, handler-free projection of this definition for REST
     * serialisation.  Avoids exposing the raw {@link HookHandler} lambda
     * over HTTP.
     *
     * @param name       hook name
     * @param description hook description
     * @param eventType  name of the {@link HookEventType}
     * @param enabled    current enabled state
     */
    public record Summary(String name, String description, String eventType, boolean enabled) {}

    /**
     * Returns a {@link Summary} snapshot of this definition.
     */
    public Summary toSummary() {
        return new Summary(name, description, eventType.name(), enabled);
    }
}
