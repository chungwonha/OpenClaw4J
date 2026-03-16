package com.chung.ai.software.openclaw4j.gateway.hook;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Fires the {@link HookEventType#GATEWAY_STARTUP} lifecycle event once the
 * Spring application context is fully initialised and the gateway is ready to
 * accept traffic.
 *
 * Using {@link ApplicationReadyEvent} (rather than {@code ContextRefreshedEvent})
 * guarantees that all beans — including bundled hooks registered via
 * {@code @PostConstruct} — are already wired and enabled before the event fires.
 */
@Component
@RequiredArgsConstructor
public class GatewayStartupHookListener implements ApplicationListener<ApplicationReadyEvent> {

    private final HookExecutor hookExecutor;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        hookExecutor.fire(HookEventType.GATEWAY_STARTUP, "system", "Gateway started");
    }
}
