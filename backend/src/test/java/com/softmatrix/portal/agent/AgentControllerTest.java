package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@Import(SecurityConfig.class)
class AgentControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService service;
    @MockBean ChatflowValidator validator; // SecurityConfig 无需,但避免上下文缺 bean

    @Test
    void list_requires_auth() throws Exception {
        mvc.perform(get("/api/agents"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void list_returns_agents_when_authenticated() throws Exception {
        when(service.list()).thenReturn(List.of(new AgentResponse(
                UUID.randomUUID(), "A", "d", "cf1", "admin",
                OffsetDateTime.now(), OffsetDateTime.now())));

        mvc.perform(get("/api/agents").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("A"));
    }

    @Test
    void create_uses_username_as_owner() throws Exception {
        when(service.create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("admin")))
            .thenReturn(new AgentResponse(UUID.randomUUID(), "A", "d", "cf1", "admin",
                    OffsetDateTime.now(), OffsetDateTime.now()));

        mvc.perform(post("/api/agents")
                .with(oidcLogin().idToken(t -> t.claim("preferred_username", "admin")))
                .contentType("application/json")
                .content("{\"name\":\"A\",\"description\":\"d\",\"flowiseChatflowId\":\"cf1\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.owner").value("admin"));
    }
}
