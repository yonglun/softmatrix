package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    AgentRepository repo;
    ChatflowValidator validator;
    AgentService service;

    @BeforeEach
    void setUp() {
        repo = mock(AgentRepository.class);
        validator = mock(ChatflowValidator.class);
        service = new AgentService(repo, validator);
        when(repo.save(any(AgentEntity.class))).thenAnswer(inv -> {
            AgentEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void create_rejects_invalid_chatflow() {
        when(validator.chatflowExists("bad")).thenReturn(false);
        AgentRequest req = new AgentRequest("A", "d", "bad");

        assertThatThrownBy(() -> service.create(req, "admin"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chatflow");
        verify(repo, never()).save(any());
    }

    @Test
    void create_saves_when_chatflow_valid() {
        when(validator.chatflowExists("good")).thenReturn(true);
        AgentRequest req = new AgentRequest("A", "d", "good");

        AgentResponse res = service.create(req, "admin");

        assertThat(res.name()).isEqualTo("A");
        assertThat(res.owner()).isEqualTo("admin");
        verify(repo).save(any(AgentEntity.class));
    }
}
