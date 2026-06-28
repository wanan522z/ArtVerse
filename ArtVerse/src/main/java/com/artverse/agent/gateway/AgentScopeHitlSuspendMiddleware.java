package com.artverse.agent.gateway;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * Middleware for HITL (Human-in-the-Loop) flow.
 * <p>
 * With the v2 external tool pattern, the agent automatically pauses when an
 * external tool returns {@link ToolResultState#RUNNING}. The framework emits
 * {@link io.agentscope.core.event.RequireExternalExecutionEvent} and handles
 * the interrupt — no manual {@code InterruptControl.trigger()} needed.
 * <p>
 * This middleware only monitors and logs for observability.
 */
public class AgentScopeHitlSuspendMiddleware implements MiddlewareBase {

    static final String ASK_USER_TOOL = "ask_user";

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx,
                                     ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        return next.apply(input)
                .doOnNext(event -> {
                    if (event instanceof ToolResultEndEvent tre
                            && ASK_USER_TOOL.equals(tre.getToolCallName())) {
                        // v2 framework already emits RequireExternalExecutionEvent
                        // and pauses the agent when externalTool returns RUNNING.
                        // No manual interrupt needed.
                    }
                });
    }
}
