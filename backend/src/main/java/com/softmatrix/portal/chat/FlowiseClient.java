package com.softmatrix.portal.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

@Component
public class FlowiseClient implements ChatflowValidator {

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public FlowiseClient(WebClient flowiseWebClient,
                         @Value("${flowise.api-key}") String apiKey) {
        this.webClient = flowiseWebClient;
        this.apiKey = apiKey;
    }

    @Override
    public boolean chatflowExists(String chatflowId) {
        try {
            webClient.get()
                    .uri("/api/v1/chatflows/{id}", chatflowId)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 调用 Flowise streaming prediction,逐 token 发出文本片段。 */
    public Flux<String> streamPrediction(String chatflowId, String sessionId, String question) {
        Map<String, Object> body = Map.of(
                "question", question,
                "streaming", true,
                "chatId", sessionId);

        return webClient.post()
                .uri("/api/v1/prediction/{id}", chatflowId)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)   // 每个 SSE data: 行作为一个元素
                .map(this::extractToken)
                .filter(s -> !s.isEmpty());
    }

    private String extractToken(String dataLine) {
        try {
            JsonNode node = mapper.readTree(dataLine);
            if ("token".equals(node.path("event").asText())) {
                return node.path("data").asText("");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
