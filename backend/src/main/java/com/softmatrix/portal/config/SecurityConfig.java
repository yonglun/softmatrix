package com.softmatrix.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            com.softmatrix.portal.auth.CustomOidcUserService oidcUserService,
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrations)
            throws Exception {
        // RP-Initiated Logout:同时终结 Keycloak SSO 会话,否则登出后会被静默重新登录
        var oidcLogout = new org.springframework.security.oauth2.client.oidc.web.logout
                .OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        oidcLogout.setPostLogoutRedirectUri("http://localhost:5173/");
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(u -> u.oidcUserService(oidcUserService))
                .defaultSuccessUrl("http://localhost:5173/", true)
            )
            .logout(logout -> logout
                .logoutSuccessHandler(oidcLogout)
            )
            // 对 /api/** 未认证返回 401,而不是 302 重定向到 Keycloak
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**"))
            )
            // SPA 通过 POST /logout 触发登出;此处禁用 CSRF 以简化切片(后续子项目加固)
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
