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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
class AgentControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService service;
    @MockBean ChatflowValidator validator;

    private AgentResponse sample(AgentStatus status) {
        return new AgentResponse(UUID.randomUUID(), "A", "d", "客服", List.of("faq"),
                "cf1", status, "admin", null, OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    void list_requires_auth() throws Exception {
        mvc.perform(get("/api/agents")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_passes_filters() throws Exception {
        when(service.list("客服", "DRAFT", "k", "faq")).thenReturn(List.of(sample(AgentStatus.DRAFT)));
        mvc.perform(get("/api/agents?category=客服&status=DRAFT&keyword=k&tag=faq").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void create_uses_username_as_owner() throws Exception {
        when(service.create(any(), eq("admin"))).thenReturn(sample(AgentStatus.DRAFT));
        mvc.perform(post("/api/agents")
                .with(oidcLogin().idToken(t -> t.claim("preferred_username", "admin")))
                .contentType("application/json")
                .content("{\"name\":\"A\",\"flowiseChatflowId\":\"cf1\",\"tags\":[\"faq\"]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.owner").value("admin"));
    }

    @Test
    void publish_endpoint() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.publish(id)).thenReturn(sample(AgentStatus.PUBLISHED));
        mvc.perform(post("/api/agents/{id}/publish", id).with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void categories_endpoint() throws Exception {
        when(service.listCategories()).thenReturn(List.of("客服", "法务"));
        mvc.perform(get("/api/agents/categories").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0]").value("客服"));
    }

    @Test
    void get_by_id_endpoint() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(sample(AgentStatus.DRAFT));
        mvc.perform(get("/api/agents/{id}", id).with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name").value("A"));
    }
}
