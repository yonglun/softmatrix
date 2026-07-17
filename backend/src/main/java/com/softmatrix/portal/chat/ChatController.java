package com.softmatrix.portal.chat;

import com.softmatrix.portal.agent.AgentEntity;
import com.softmatrix.portal.agent.AgentService;
import com.softmatrix.portal.chat.dto.ChatRequest;
import com.softmatrix.portal.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class ChatController {

    private final AgentService agentService;
    private final FlowiseClient flowiseClient;

    public ChatController(AgentService agentService, FlowiseClient flowiseClient) {
        this.agentService = agentService;
        this.flowiseClient = flowiseClient;
    }

    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("@perm.has('AGENT_RUN')")
    public Flux<String> chat(@PathVariable UUID id, @Valid @RequestBody ChatRequest req) {
        AgentEntity agent = agentService.find(id);
        if (agent.getStatus() != com.softmatrix.portal.agent.AgentStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "AGENT_NOT_RUNNABLE",
                    "该 Agent 未发布,无法运行");
        }
        return flowiseClient.streamPrediction(
                        agent.getFlowiseChatflowId(), req.sessionId(), req.message())
                .onErrorMap(ex -> new ApiException(HttpStatus.BAD_GATEWAY,
                        "FLOWISE_ERROR", "运行失败,请重试"));
    }
}
