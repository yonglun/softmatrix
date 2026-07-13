package com.softmatrix.portal.agent;

import com.fasterxml.jackson.databind.node.TextNode;
import com.softmatrix.portal.agent.dto.AgentPackage;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.chat.FlowiseClient;
import com.softmatrix.portal.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentImportExportTest {

    AgentRepository repo;
    ChatflowValidator validator;
    FlowiseClient flowise;
    AgentService service;

    @BeforeEach
    void setUp() {
        repo = mock(AgentRepository.class);
        validator = mock(ChatflowValidator.class);
        flowise = mock(FlowiseClient.class);
        service = new AgentService(repo, validator, flowise);
        when(repo.save(any(AgentEntity.class))).thenAnswer(inv -> {
            AgentEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void export_packs_metadata_and_flow() {
        AgentEntity e = new AgentEntity();
        e.setId(UUID.randomUUID());
        e.setName("客服"); e.setDescription("d"); e.setCategory("客服");
        e.setTags(new String[]{"faq"}); e.setFlowiseChatflowId("cf1");
        when(repo.findById(e.getId())).thenReturn(Optional.of(e));
        com.fasterxml.jackson.databind.node.ObjectNode cf =
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
        cf.put("name", "Demo");
        cf.set("flowData", TextNode.valueOf("{\"nodes\":[]}"));
        when(flowise.getChatflow("cf1")).thenReturn(cf);

        AgentPackage pkg = service.export(e.getId());

        assertThat(pkg.agent().name()).isEqualTo("客服");
        assertThat(pkg.agent().tags()).containsExactly("faq");
        assertThat(pkg.flow().name()).isEqualTo("Demo");
        assertThat(pkg.flow().flowData().asText()).contains("nodes");
    }

    @Test
    void import_creates_flow_and_draft_agent() {
        AgentPackage.AgentMeta meta = new AgentPackage.AgentMeta("导入的", "d", "市场", List.of("gen"));
        AgentPackage.FlowMeta flow = new AgentPackage.FlowMeta("流", TextNode.valueOf("{\"nodes\":[]}"));
        AgentPackage pkg = new AgentPackage("1", meta, flow);
        when(flowise.createChatflow(eq("流"), any())).thenReturn("new-cf");

        var res = service.importPackage(pkg, "admin");

        assertThat(res.status()).isEqualTo(AgentStatus.DRAFT);
        assertThat(res.flowiseChatflowId()).isEqualTo("new-cf");
        assertThat(res.owner()).isEqualTo("admin");
        assertThat(res.category()).isEqualTo("市场");
    }

    @Test
    void import_rejects_missing_name_or_flow() {
        AgentPackage bad = new AgentPackage("1",
                new AgentPackage.AgentMeta(null, null, null, null), null);
        assertThatThrownBy(() -> service.importPackage(bad, "admin"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "IMPORT_INVALID");
    }
}
