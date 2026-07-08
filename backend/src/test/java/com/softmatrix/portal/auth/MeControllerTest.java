package com.softmatrix.portal.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeController.class)
@Import(com.softmatrix.portal.config.SecurityConfig.class)
class MeControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void unauthenticated_returns_401() throws Exception {
        mvc.perform(get("/api/me"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_returns_username() throws Exception {
        mvc.perform(get("/api/me").with(oidcLogin()
                .idToken(t -> t.claim("preferred_username", "admin")
                               .claim("name", "Platform Admin"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.username").value("admin"))
           .andExpect(jsonPath("$.name").value("Platform Admin"));
    }
}
