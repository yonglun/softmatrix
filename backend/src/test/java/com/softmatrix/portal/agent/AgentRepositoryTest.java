package com.softmatrix.portal.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AgentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    AgentRepository repo;

    private AgentEntity agent(String name, String category, AgentStatus status, String... tags) {
        AgentEntity e = new AgentEntity();
        e.setName(name);
        e.setCategory(category);
        e.setStatus(status);
        e.setTags(tags);
        e.setFlowiseChatflowId("cf");
        e.setOwner("admin");
        return e;
    }

    @Test
    void search_filters_by_category_status_keyword_tag() {
        repo.save(agent("客服助手", "客服", AgentStatus.PUBLISHED, "faq", "zh"));
        repo.save(agent("合同审查", "法务", AgentStatus.DRAFT, "legal"));

        assertThat(repo.search("客服", null, null, null)).hasSize(1);
        assertThat(repo.search(null, "DRAFT", null, null)).hasSize(1);
        assertThat(repo.search(null, null, "合同", null)).hasSize(1);
        assertThat(repo.search(null, null, null, "faq")).hasSize(1);
        assertThat(repo.search(null, null, null, "nope")).isEmpty();
        assertThat(repo.search(null, null, null, null)).hasSize(2);
    }

    @Test
    void distinct_categories_and_tags() {
        repo.save(agent("a", "客服", AgentStatus.PUBLISHED, "faq", "zh"));
        repo.save(agent("b", "法务", AgentStatus.DRAFT, "faq"));

        assertThat(repo.findDistinctCategories()).containsExactly("客服", "法务");
        assertThat(repo.findDistinctTags()).containsExactly("faq", "zh");
    }
}
