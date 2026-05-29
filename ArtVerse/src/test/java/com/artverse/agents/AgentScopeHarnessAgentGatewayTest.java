package com.artverse.agents;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeHarnessAgentGatewayTest {

    @Test
    void mergesSystemMessagesIntoFirstUserMessage() {
        AgentRunRequest request = new AgentRunRequest(
                "default",
                1L,
                2L,
                AgentTaskType.STORYBOARD,
                List.of(
                        new AgentMessage("system", "系统提示"),
                        new AgentMessage("user", "用户内容")
                ),
                Map.of()
        );

        List<AgentMessage> messages = AgentScopeHarnessAgentGateway.prepareInputMessages(request);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).role()).isEqualTo("user");
        assertThat(messages.get(0).content()).contains("系统提示", "用户内容");
    }
}
