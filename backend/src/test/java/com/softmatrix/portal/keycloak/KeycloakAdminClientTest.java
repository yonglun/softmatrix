package com.softmatrix.portal.keycloak;

import com.softmatrix.portal.common.ApiException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakAdminClientTest {

    MockWebServer server;
    KeycloakAdminClient client;

    private static final String TOKEN_JSON =
            "{\"access_token\":\"tk1\",\"expires_in\":300}";

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        WebClient wc = WebClient.builder().baseUrl(server.url("/").toString()).build();
        client = new KeycloakAdminClient(wc, "softmatrix", "portal-admin", "secret");
    }

    @AfterEach
    void tearDown() throws Exception { server.shutdown(); }

    private void enqueueToken() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody(TOKEN_JSON).addHeader("Content-Type", "application/json"));
    }

    @Test
    void createUser_returns_id_from_location_header() throws Exception {
        enqueueToken();
        server.enqueue(new MockResponse().setResponseCode(201)
                .addHeader("Location", "/admin/realms/softmatrix/users/kc-123"));

        String id = client.createUser("alice", "Alice", "a@x.com", "Passw0rd!");

        assertThat(id).isEqualTo("kc-123");
        RecordedRequest tokenReq = server.takeRequest();
        assertThat(tokenReq.getPath()).isEqualTo("/realms/softmatrix/protocol/openid-connect/token");
        RecordedRequest createReq = server.takeRequest();
        assertThat(createReq.getPath()).isEqualTo("/admin/realms/softmatrix/users");
        assertThat(createReq.getHeader("Authorization")).isEqualTo("Bearer tk1");
        assertThat(createReq.getBody().readUtf8()).contains("\"temporary\":true");
    }

    @Test
    void createUser_409_maps_to_username_taken() {
        enqueueToken();
        server.enqueue(new MockResponse().setResponseCode(409));

        assertThatThrownBy(() -> client.createUser("dup", null, null, "Passw0rd!"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "USERNAME_TAKEN");
    }

    @Test
    void kc_5xx_maps_to_keycloak_error() {
        enqueueToken();
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.setEnabled("kc-1", false))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "KEYCLOAK_ERROR");
    }

    @Test
    void token_is_cached_across_calls() throws Exception {
        enqueueToken();
        server.enqueue(new MockResponse().setResponseCode(204));
        server.enqueue(new MockResponse().setResponseCode(204));

        client.setEnabled("kc-1", true);
        client.resetPassword("kc-1", "NewPassw0rd!");

        // 3 个请求:1 token + 2 API —— token 未重复获取
        assertThat(server.getRequestCount()).isEqualTo(3);
    }
}
