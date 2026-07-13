package com.softmatrix.portal.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigController.class)
@Import({SecurityConfig.class, com.softmatrix.portal.TestOAuth2Config.class})
@TestPropertySource(properties = {
        "flowise.base-url=http://internal:3000",
        "flowise.designer-base-url=http://designer.example:3000"})
class ConfigControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void requires_auth() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void returns_designer_base_url() throws Exception {
        mvc.perform(get("/api/config").with(oidcLogin()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.designerBaseUrl").value("http://designer.example:3000"));
    }
}
