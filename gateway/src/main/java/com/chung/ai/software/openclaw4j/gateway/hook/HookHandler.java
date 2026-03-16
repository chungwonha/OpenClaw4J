package com.chung.ai.software.openclaw4j.gateway.hook;

/**
 * Single-method contract that every hook implementation must satisfy.
 *
 * Implementations receive a fully-populated {@link HookContext} and may
 * optionally set {@link HookContext#setResult(String)} to communicate output
 * back to the caller.  Implementations must not throw unchecked exceptions —
 * {@link HookExecutor} wraps each call in a try/catch, but a clean handler
 * is always preferable.
 */
@FunctionalInterface
public interface HookHandler {

    /**
     * Execute the hook logic for the given context.
     *
     * @param context event context; {@code context.setResult(...)} may be called
     *                to pass output back to the caller
     */
    void handle(HookContext context);
}
