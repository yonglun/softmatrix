package com.softmatrix.portal.chat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowiseClientTest {

    MockWebServer server;
    FlowiseClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        WebClient wc = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        client = new FlowiseClient(wc, "test-key");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void chatflowExists_true_on_200() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"cf1\"}")
                .addHeader("Content-Type", "application/json"));

        assertThat(client.chatflowExists("cf1")).isTrue();
    }

    @Test
    void chatflowExists_false_on_404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(client.chatflowExists("missing")).isFalse();
    }

    @Test
    void streamPrediction_emits_tokens() {
        // Flowise streaming 以 SSE 返回,每个 token 一行 data:
        String sse = "message:\ndata:{\"event\":\"token\",\"data\":\"Hello\"}\n\n"
                   + "message:\ndata:{\"event\":\"token\",\"data\":\" world\"}\n\n";
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody(sse)
                .addHeader("Content-Type", "text/event-stream"));

        StepVerifier.create(client.streamPrediction("cf1", "sess1", "hi"))
                .expectNext("Hello")
                .expectNext(" world")
                .verifyComplete();
    }

    @Test
    void getChatflow_returns_json() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"cf1\",\"name\":\"Demo\",\"flowData\":\"{\\\"nodes\\\":[]}\"}")
                .addHeader("Content-Type", "application/json"));

        com.fasterxml.jackson.databind.JsonNode node = client.getChatflow("cf1");
        assertThat(node.path("name").asText()).isEqualTo("Demo");
        assertThat(node.path("flowData").asText()).contains("nodes");
    }

    @Test
    void createChatflow_returns_new_id() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"new-cf\",\"name\":\"Imported\"}")
                .addHeader("Content-Type", "application/json"));

        com.fasterxml.jackson.databind.node.TextNode flowData =
                com.fasterxml.jackson.databind.node.TextNode.valueOf("{\"nodes\":[]}");
        String id = client.createChatflow("Imported", flowData);
        assertThat(id).isEqualTo("new-cf");
    }

    @Test
    void createChatflow_sends_type_field() throws InterruptedException {
        // Flowise's create-chatflow endpoint rejects requests missing "type"
        // with 400 "Invalid Chatflow Type" — verified against a real running
        // Flowise instance during end-to-end testing. This test pins the
        // request body shape so a regression here fails fast, not just in prod.
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"new-cf\"}")
                .addHeader("Content-Type", "application/json"));

        com.fasterxml.jackson.databind.node.TextNode flowData =
                com.fasterxml.jackson.databind.node.TextNode.valueOf("{\"nodes\":[]}");
        client.createChatflow("Imported", flowData);

        okhttp3.mockwebserver.RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getBody().readUtf8()).contains("\"type\":\"CHATFLOW\"");
    }

    @Test
    void getChatflow_throws_502_on_error() {
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> client.getChatflow("cf1"))
                .isInstanceOf(com.softmatrix.portal.common.ApiException.class)
                .hasFieldOrPropertyWithValue("code", "FLOWISE_ERROR");
    }
}
