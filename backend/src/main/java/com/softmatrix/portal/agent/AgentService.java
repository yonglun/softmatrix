package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AgentService {

    private final AgentRepository repo;
    private final ChatflowValidator validator;

    public AgentService(AgentRepository repo, ChatflowValidator validator) {
        this.repo = repo;
        this.validator = validator;
    }

    public List<AgentResponse> list(String category, String status, String keyword, String tag) {
        AgentStatus st = parseStatus(status);
        return repo.search(emptyToNull(category), st == null ? null : st.name(),
                        emptyToNull(keyword), emptyToNull(tag))
                .stream().map(AgentResponse::from).toList();
    }

    public List<String> listCategories() { return repo.findDistinctCategories(); }

    public List<String> listTags() { return repo.findDistinctTags(); }

    public AgentResponse create(AgentRequest req, String owner) {
        if (req.flowiseChatflowId() == null || req.flowiseChatflowId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHATFLOW",
                    "创建 Agent 必须提供 Chatflow ID");
        }
        requireChatflow(req.flowiseChatflowId());
        AgentEntity e = new AgentEntity();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setCategory(req.category());
        e.setTags(toArray(req.tags()));
        e.setFlowiseChatflowId(req.flowiseChatflowId());
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

    public AgentEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
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
