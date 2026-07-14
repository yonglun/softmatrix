package com.softmatrix.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {

    private final String designerBaseUrl;

    public ConfigController(
            @Value("${flowise.designer-base-url:${flowise.base-url}}") String designerBaseUrl) {
        this.designerBaseUrl = designerBaseUrl;
    }

    public record AppConfig(String designerBaseUrl) {}

    /** 下发浏览器侧需要的运行时配置。designerBaseUrl 须是浏览器可达地址。 */
    @GetMapping("/api/config")
    public AppConfig config() {
        return new AppConfig(designerBaseUrl);
    }
}
