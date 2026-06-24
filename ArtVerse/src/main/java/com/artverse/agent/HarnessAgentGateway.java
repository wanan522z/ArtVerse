package com.artverse.agent;

import com.artverse.agent.AgentRunRequest;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HarnessAgentGateway {
    Flux<String> streamChat(AgentRunRequest request);
    Flux<AgentEvent> streamEvents(AgentRunRequest request);
    Mono<Msg> generate(AgentRunRequest request);
}