package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
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

    private AgentEntity stored(AgentStatus status) {
        AgentEntity e = new AgentEntity();
        e.setId(UUID.randomUUID());
        e.setName("A");
        e.setFlowiseChatflowId("cf1");
        e.setOwner("admin");
        e.setStatus(status);
        when(repo.findById(e.getId())).thenReturn(java.util.Optional.of(e));
        return e;
    }

    @Test
    void create_defaults_to_draft_and_sets_metadata() {
        when(validator.chatflowExists("cf1")).thenReturn(true);
        AgentRequest req = new AgentRequest("A", "d", "客服", List.of("faq", "zh"), "cf1");

        AgentResponse res = service.create(req, "admin");

        assertThat(res.status()).isEqualTo(AgentStatus.DRAFT);
        assertThat(res.category()).isEqualTo("客服");
        assertThat(res.tags()).containsExactly("faq", "zh");
        assertThat(res.owner()).isEqualTo("admin");
    }

    @Test
    void create_rejects_blank_chatflow() {
        AgentRequest req = new AgentRequest("A", "d", null, null, "  ");
        assertThatThrownBy(() -> service.create(req, "admin"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Chatflow");
        verify(repo, never()).save(any());
    }

    @Test
    void update_ignores_chatflow_and_status() {
        AgentEntity e = stored(AgentStatus.PUBLISHED);
        e.setFlowiseChatflowId("original");
        AgentRequest req = new AgentRequest("A2", "d2", "法务", List.of("t"), "SHOULD-BE-IGNORED");

        AgentResponse res = service.update(e.getId(), req);

        assertThat(res.name()).isEqualTo("A2");
        assertThat(res.flowiseChatflowId()).isEqualTo("original"); // 未被改
        assertThat(res.status()).isEqualTo(AgentStatus.PUBLISHED);  // 未被改
    }

    @Test
    void publish_from_draft_sets_published_at() {
        AgentEntity e = stored(AgentStatus.DRAFT);
        AgentResponse res = service.publish(e.getId());
        assertThat(res.status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(res.publishedAt()).isNotNull();
    }

    @Test
    void disable_from_draft_is_illegal() {
        AgentEntity e = stored(AgentStatus.DRAFT);
        assertThatThrownBy(() -> service.disable(e.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_TRANSITION");
    }

    @Test
    void withdraw_from_published_returns_to_draft() {
        AgentEntity e = stored(AgentStatus.PUBLISHED);
        assertThat(service.withdraw(e.getId()).status()).isEqualTo(AgentStatus.DRAFT);
    }

    @Test
    void reenable_from_disabled_to_published() {
        AgentEntity e = stored(AgentStatus.DISABLED);
        assertThat(service.publish(e.getId()).status()).isEqualTo(AgentStatus.PUBLISHED);
    }
}
