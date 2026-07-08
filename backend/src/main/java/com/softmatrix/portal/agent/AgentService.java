package com.softmatrix.portal.agent;

import com.softmatrix.portal.agent.dto.AgentRequest;
import com.softmatrix.portal.agent.dto.AgentResponse;
import com.softmatrix.portal.chat.ChatflowValidator;
import com.softmatrix.portal.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

    public List<AgentResponse> list() {
        return repo.findAll().stream().map(AgentResponse::from).toList();
    }

    public AgentResponse create(AgentRequest req, String owner) {
        requireChatflow(req.flowiseChatflowId());
        AgentEntity e = new AgentEntity();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setFlowiseChatflowId(req.flowiseChatflowId());
        e.setOwner(owner);
        return AgentResponse.from(repo.save(e));
    }

    public AgentResponse update(UUID id, AgentRequest req) {
        AgentEntity e = find(id);
        requireChatflow(req.flowiseChatflowId());
        e.setName(req.name());
        e.setDescription(req.description());
        e.setFlowiseChatflowId(req.flowiseChatflowId());
        return AgentResponse.from(repo.save(e));
    }

    public void delete(UUID id) {
        repo.delete(find(id));
    }

    public AgentEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
    }

    private void requireChatflow(String chatflowId) {
        if (!validator.chatflowExists(chatflowId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHATFLOW",
                    "指定的 Chatflow 不存在,请检查 Chatflow ID");
        }
    }
}
