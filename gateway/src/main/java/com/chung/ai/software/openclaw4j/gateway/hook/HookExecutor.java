package com.chung.ai.software.openclaw4j.gateway.hook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fires lifecycle hooks for a given {@link HookEventType}.
 *
 * For each fire call this component:
 *  1. Builds a {@link HookContext} with the supplied arguments.
 *  2. Retrieves all enabled handlers for the event type from {@link HookRegistry}.
 *  3. Invokes each handler in registration order, catching any exception
 *     individually so that a misbehaving hook cannot prevent other hooks from
 *     running or break the caller's execution path.
 *  4. Returns the populated context so the caller can inspect
 *     {@link HookContext#getResult()}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HookExecutor {

    private final HookRegistry hookRegistry;

    /**
     * Fires all enabled hooks registered for {@code eventType}.
     *
     * @param eventType the lifecycle event
     * @param sessionId the session in whose context the event occurred
     * @param payload   event-specific text payload
     * @return the {@link HookContext} after all handlers have run
     */
    public HookContext fire(HookEventType eventType, String sessionId, String payload) {
        return fire(eventType, sessionId, payload, Map.of());
    }

    /**
     * Fires all enabled hooks registered for {@code eventType}, passing
     * additional key/value attributes through the context.
     *
     * @param eventType  the lifecycle event
     * @param sessionId  the session in whose context the event occurred
     * @param payload    event-specific text payload
     * @param attributes additional metadata for handlers
     * @return the {@link HookContext} after all handlers have run
     */
    public HookContext fire(HookEventType eventType,
                            String sessionId,
                            String payload,
                            Map<String, Object> attributes) {

        HookContext context = HookContext.builder()
                .eventType(eventType)
                .sessionId(sessionId)
                .payload(payload != null ? payload : "")
                .attributes(attributes != null ? attributes : Map.of())
                .timestamp(Instant.now())
                .build();

        List<HookDefinition> handlers = hookRegistry.getEnabledHandlersFor(eventType);

        if (handlers.isEmpty()) {
            log.debug("[HookExecutor] No enabled hooks for event={}", eventType);
            return context;
        }

        log.debug("[HookExecutor] Firing event={} session='{}' handlers={}",
                eventType, sessionId, handlers.size());

        for (HookDefinition definition : handlers) {
            log.debug("[HookExecutor] Invoking hook='{}' for event={}", definition.getName(), eventType);
            try {
                definition.getHandler().handle(context);
                log.debug("[HookExecutor] Hook='{}' completed for event={}", definition.getName(), eventType);
            } catch (Exception e) {
                log.error("[HookExecutor] Hook='{}' threw an exception for event={} session='{}'",
                        definition.getName(), eventType, sessionId, e);
            }
        }

        return context;
    }
}
