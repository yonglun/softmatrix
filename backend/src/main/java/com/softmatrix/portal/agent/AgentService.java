package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class AgentService {

    private final AgentRepository repo;
    private final ChatflowValidator validator;
    private final com.softmatrix.portal.chat.FlowiseClient flowise;

    /** Flowise 的 flowData 是 JSON 字符串;空白画布形态与导入功能一致。 */
    private static final com.fasterxml.jackson.databind.JsonNode BLANK_FLOW_DATA =
            com.fasterxml.jackson.databind.node.TextNode.valueOf("{\"nodes\":[],\"edges\":[]}");

    public AgentService(AgentRepository repo, ChatflowValidator validator,
                        com.softmatrix.portal.chat.FlowiseClient flowise) {
        this.repo = repo;
        this.validator = validator;
        this.flowise = flowise;
    }

    public List<AgentResponse> list(String category, String status, String keyword, String tag) {
        AgentStatus st = parseStatus(status);
        return repo.search(emptyToNull(category), st == null ? null : st.name(),
                        emptyToNull(keyword), emptyToNull(tag))
                .stream().map(AgentResponse::from).toList();
    }

    public List<String> listCategories() { return repo.findDistinctCategories(); }

    public List<String> listTags() { return repo.findDistinctTags(); }

    /** 留空 chatflowId 时自动在 Flowise 创建空白流并绑定;有值时校验后登记。 */
    public AgentResponse create(AgentRequest req, String owner) {
        String chatflowId;
        if (req.flowiseChatflowId() == null || req.flowiseChatflowId().isBlank()) {
            chatflowId = flowise.createChatflow(req.name(), BLANK_FLOW_DATA);
        } else {
            requireChatflow(req.flowiseChatflowId());
            chatflowId = req.flowiseChatflowId();
        }
        AgentEntity e = new AgentEntity();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setCategory(req.category());
        e.setTags(toArray(req.tags()));
        e.setFlowiseChatflowId(chatflowId);
        e.setOwner(owner);
        e.setStatus(AgentStatus.DRAFT);
        return AgentResponse.from(repo.save(e));
    }

    /** 更新只改元数据;忽略 flowiseChatflowId 与 status。 */
    public AgentResponse update(UUID id, AgentRequest req) {
        AgentEntity e = find(id);
        e.setName(req.name());
        e.setDescription(req.description());
        e.setCategory(req.category());
        e.setTags(toArray(req.tags()));
        return AgentResponse.from(repo.save(e));
    }

    public void delete(UUID id) { repo.delete(find(id)); }

    public AgentResponse publish(UUID id)  { return transition(id, AgentStatus.PUBLISHED); }
    public AgentResponse disable(UUID id)  { return transition(id, AgentStatus.DISABLED); }
    public AgentResponse withdraw(UUID id) { return transition(id, AgentStatus.DRAFT); }

    private AgentResponse transition(UUID id, AgentStatus target) {
        AgentEntity e = find(id);
        if (!e.getStatus().canTransitionTo(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSITION",
                    "不允许从 " + e.getStatus() + " 转换到 " + target);
        }
        e.setStatus(target);
        if (target == AgentStatus.PUBLISHED) {
            e.setPublishedAt(OffsetDateTime.now());
        }
        return AgentResponse.from(repo.save(e));
    }

    public AgentResponse get(UUID id) { return AgentResponse.from(find(id)); }

    public AgentEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
    }

    public com.softmatrix.portal.agent.dto.AgentPackage export(UUID id) {
        AgentEntity e = find(id);
        com.fasterxml.jackson.databind.JsonNode cf = flowise.getChatflow(e.getFlowiseChatflowId());
        var agentMeta = new com.softmatrix.portal.agent.dto.AgentPackage.AgentMeta(
                e.getName(), e.getDescription(), e.getCategory(),
                e.getTags() == null ? java.util.List.of() : java.util.Arrays.asList(e.getTags()));
        var flowMeta = new com.softmatrix.portal.agent.dto.AgentPackage.FlowMeta(
                cf.path("name").asText(e.getName()), cf.path("flowData"));
        return new com.softmatrix.portal.agent.dto.AgentPackage("1", agentMeta, flowMeta);
    }

    public AgentResponse importPackage(com.softmatrix.portal.agent.dto.AgentPackage pkg, String owner) {
        if (pkg.agent() == null || pkg.agent().name() == null || pkg.agent().name().isBlank()
                || pkg.flow() == null || pkg.flow().flowData() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_INVALID",
                    "导入文件结构非法:缺少 agent.name 或 flow.flowData");
        }
        String flowName = pkg.flow().name() == null ? pkg.agent().name() : pkg.flow().name();
        String newChatflowId = flowise.createChatflow(flowName, pkg.flow().flowData());

        AgentEntity e = new AgentEntity();
        e.setName(pkg.agent().name());
        e.setDescription(pkg.agent().description());
        e.setCategory(pkg.agent().category());
        e.setTags(pkg.agent().tags() == null ? new String[0]
                : pkg.agent().tags().toArray(new String[0]));
        e.setFlowiseChatflowId(newChatflowId);
        e.setOwner(owner);
        e.setStatus(AgentStatus.DRAFT);
        return AgentResponse.from(repo.save(e));
    }

    void requireChatflow(String chatflowId) {
        if (!validator.chatflowExists(chatflowId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHATFLOW",
                    "指定的 Chatflow 不存在,请检查 Chatflow ID");
        }
    }

    private static String[] toArray(List<String> tags) {
        return tags == null ? new String[0] : tags.toArray(new String[0]);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static AgentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return AgentStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "未知状态: " + status);
        }
    }
}
