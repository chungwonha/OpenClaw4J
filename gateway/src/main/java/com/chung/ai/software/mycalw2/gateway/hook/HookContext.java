package com.chung.ai.software.mycalw2.gateway.hook;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of the event that triggered a hook, plus a mutable
 * {@code result} field that hook handlers can write to so the caller can
 * inspect any output the hook produced.
 *
 * Use {@link HookContext#builder()} to construct instances.
 */
@Getter
@Builder
public class HookContext {

    /** The lifecycle event that caused this hook to fire. */
    private final HookEventType eventType;

    /** The session (conversation) in whose context the event occurred. */
    private final String sessionId;

    /** Event-specific text payload — may be empty but never null. */
    private final String payload;

    /**
     * Arbitrary key/value attributes for event-specific metadata that does
     * not belong in the structured fields above.
     */
    @Builder.Default
    private final Map<String, Object> attributes = new HashMap<>();

    /** Wall-clock time at which the HookContext was created. */
    private final Instant timestamp;

    /**
     * Mutable output slot.  Hook handlers may set this to communicate a
     * result back to the caller (e.g., content to inject, a status message).
     * Callers read it after {@link HookExecutor#fire} returns.
     */
    @Setter
    private String result;
}
