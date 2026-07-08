package com.softmatrix.portal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * 为 @WebMvcTest 切片测试提供一个静态构造的 ClientRegistrationRepository。
 *
 * <p>生产配置用 issuer-uri,Spring 会在上下文启动时联网访问 Keycloak 的
 * discovery 端点解析各端点;这会让 Web 切片测试隐式依赖 Keycloak 处于运行状态。
 * 这里显式提供 ClientRegistrationRepository bean(端点写死、不联网),使
 * {@code OAuth2ClientRegistrationRepositoryConfiguration}(@ConditionalOnMissingBean)
 * 不再触发 discovery,从而让测试保持自包含。
 */
@TestConfiguration
public class TestOAuth2Config {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration keycloak = ClientRegistration.withRegistrationId("keycloak")
                .clientId("portal")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("http://localhost:8081/realms/softmatrix/protocol/openid-connect/auth")
                .tokenUri("http://localhost:8081/realms/softmatrix/protocol/openid-connect/token")
                .userInfoUri("http://localhost:8081/realms/softmatrix/protocol/openid-connect/userinfo")
                .jwkSetUri("http://localhost:8081/realms/softmatrix/protocol/openid-connect/certs")
                .userNameAttributeName("preferred_username")
                .build();
        return new InMemoryClientRegistrationRepository(keycloak);
    }
}
