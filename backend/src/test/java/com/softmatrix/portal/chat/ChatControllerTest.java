package com.softmatrix.portal.chat;

import com.softmatrix.portal.agent.AgentEntity;
import com.softmatrix.portal.agent.AgentService;
import com.softmatrix.portal.agent.AgentStatus;
import com.softmatrix.portal.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
class ChatControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService agentService;
    @MockBean FlowiseClient flowiseClient;
    @MockBean com.softmatrix.portal.auth.CustomOidcUserService oidcUserService;

    private AgentEntity agent(AgentStatus status) {
        AgentEntity e = new AgentEntity();
        e.setFlowiseChatflowId("cf1");
        e.setStatus(status);
        return e;
    }

    @Test
    void chat_requires_auth() throws Exception {
        mvc.perform(post("/api/agents/{id}/chat", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void chat_rejected_when_not_published() throws Exception {
        UUID id = UUID.randomUUID();
        when(agentService.find(id)).thenReturn(agent(AgentStatus.DRAFT));
        mvc.perform(post("/api/agents/{id}/chat", id).with(oidcLogin())
                .contentType("application/json")
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
           .andExpect(status().isConflict());
    }

    @Test
    void chat_streams_when_published() throws Exception {
        UUID id = UUID.randomUUID();
        when(agentService.find(id)).thenReturn(agent(AgentStatus.PUBLISHED));
        when(flowiseClient.streamPrediction(eq("cf1"), eq("s1"), eq("hi")))
                .thenReturn(Flux.just("Hello"));
        mvc.perform(post("/api/agents/{id}/chat", id).with(oidcLogin())
                .contentType("application/json")
                .content("{\"sessionId\":\"s1\",\"message\":\"hi\"}"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
