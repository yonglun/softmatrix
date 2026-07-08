package com.softmatrix.portal.chat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

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
}
