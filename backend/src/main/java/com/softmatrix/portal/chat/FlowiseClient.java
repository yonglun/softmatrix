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

    /** 取 Chatflow 完整定义(含 flowData),供导出。失败映射为 502。 */
    public JsonNode getChatflow(String chatflowId) {
        try {
            String body = webClient.get()
                    .uri("/api/v1/chatflows/{id}", chatflowId)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new com.softmatrix.portal.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "FLOWISE_ERROR", "读取 Flowise 流失败");
        }
    }

    /** 在 Flowise 新建 Chatflow,返回新 id,供导入。失败映射为 502。 */
    public String createChatflow(String name, JsonNode flowData) {
        try {
            java.util.Map<String, Object> req = new java.util.HashMap<>();
            req.put("name", name);
            req.put("flowData", flowData);
            req.put("type", "CHATFLOW");
            String body = webClient.post()
                    .uri("/api/v1/chatflows")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return mapper.readTree(body).path("id").asText();
        } catch (Exception e) {
            throw new com.softmatrix.portal.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "FLOWISE_ERROR", "在 Flowise 新建流失败");
        }
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
