package com.chung.ai.software.mycalw2.gateway.cron;

import com.chung.ai.software.mycalw2.gateway.eventqueue.EventQueue;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEvent;
import com.chung.ai.software.mycalw2.gateway.eventqueue.GatewayEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Manages the lifecycle of scheduled cron jobs.
 *
 * When a CronDefinition is registered and enabled, this scheduler creates a
 * Spring-managed ScheduledFuture using the definition's cron expression.
 * When fired, it enqueues a CRON GatewayEvent for AgentDispatcher to process.
 *
 * Uses CronTrigger (Spring's implementation of cron with a seconds field):
 *   Format: "second minute hour day-of-month month day-of-week"
 *   Example: "0 * * * * *" fires every minute at second 0.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CronScheduler {

    private final TaskScheduler taskScheduler;
    private final EventQueue eventQueue;

    /** Active ScheduledFutures, keyed by CronDefinition id. */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * Schedule a cron job. If the definition is disabled, it is registered in the
     * registry but not scheduled. If a job with the same id is already scheduled,
     * it is cancelled first (allows re-scheduling on update).
     */
    public void schedule(CronDefinition definition) {
        cancel(definition.getId()); // no-op if not already scheduled

        if (!definition.isEnabled()) {
            log.info("[CronScheduler] Job='{}' registered but not scheduled (disabled)", definition.getId());
            return;
        }

        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> fire(definition),
                    new CronTrigger(definition.getCronExpression())
            );
            futures.put(definition.getId(), future);
            log.info("[CronScheduler] Scheduled job='{}' name='{}' cron='{}'",
                    definition.getId(), definition.getName(), definition.getCronExpression());
        } catch (IllegalArgumentException e) {
            log.error("[CronScheduler] Invalid cron expression '{}' for job='{}': {}",
                    definition.getCronExpression(), definition.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Cancel a scheduled job. Safe to call even if the job is not currently scheduled.
     */
    public void cancel(String id) {
        ScheduledFuture<?> future = futures.remove(id);
        if (future != null) {
            future.cancel(false);
            log.info("[CronScheduler] Cancelled job='{}'", id);
        }
    }

    private void fire(CronDefinition definition) {
        String sessionId = definition.getSessionId() != null
                ? definition.getSessionId()
                : "cron:" + definition.getId();

        log.debug("[CronScheduler] Firing job='{}' name='{}' session='{}'",
                definition.getId(), definition.getName(), sessionId);

        GatewayEvent event = GatewayEvent.builder()
                .type(GatewayEventType.CRON)
                .sessionId(sessionId)
                .payload(definition.getName())
                .metadata(CronContext.builder().definition(definition).build())
                .timestamp(Instant.now())
                .build();

        eventQueue.enqueue(event);
    }
}
