package com.softmatrix.portal.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softmatrix.portal.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Keycloak Admin REST API 薄封装:只做本子项目用到的用户写通操作。 */
@Component
public class KeycloakAdminClient {

    private final WebClient webClient;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public KeycloakAdminClient(WebClient keycloakWebClient,
                               @Value("${keycloak.admin.realm}") String realm,
                               @Value("${keycloak.admin.client-id}") String clientId,
                               @Value("${keycloak.admin.client-secret}") String clientSecret) {
        this.webClient = keycloakWebClient;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** 建用户,返回 Keycloak user id;409 → USERNAME_TAKEN。初始密码 temporary,首登强制改密。 */
    public String createUser(String username, String name, String email, String password) {
        Map<String, Object> body = Map.of(
                "username", username,
                "firstName", name == null ? "" : name,
                "email", email == null ? "" : email,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of(
                        "type", "password", "value", password, "temporary", true)));
        try {
            var resp = webClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            String location = resp.getHeaders().getFirst(HttpHeaders.LOCATION);
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已存在");
            }
            throw kcError();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw kcError();
        }
    }

    public void updateUser(String keycloakId, String name, String email) {
        put("/admin/realms/{realm}/users/{id}",
                Map.of("firstName", name == null ? "" : name,
                       "email", email == null ? "" : email),
                keycloakId);
    }

    public void setEnabled(String keycloakId, boolean enabled) {
        put("/admin/realms/{realm}/users/{id}", Map.of("enabled", enabled), keycloakId);
    }

    public void resetPassword(String keycloakId, String password) {
        put("/admin/realms/{realm}/users/{id}/reset-password",
                Map.of("type", "password", "value", password, "temporary", true),
                keycloakId);
    }

    private void put(String uri, Object body, String keycloakId) {
        try {
            webClient.put()
                    .uri(uri, realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw kcError();
        }
    }

    private synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) return cachedToken;
        try {
            String body = webClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue("grant_type=client_credentials&client_id=" + clientId
                            + "&client_secret=" + clientSecret)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode node = mapper.readTree(body);
            cachedToken = node.path("access_token").asText();
            tokenExpiry = Instant.now().plusSeconds(Math.max(node.path("expires_in").asLong(60) - 10, 10));
            return cachedToken;
        } catch (Exception e) {
            throw kcError();
        }
    }

    private ApiException kcError() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "KEYCLOAK_ERROR", "Keycloak 操作失败");
    }
}
