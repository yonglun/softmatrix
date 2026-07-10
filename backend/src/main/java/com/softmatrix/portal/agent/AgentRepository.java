package com.softmatrix.portal.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {

    /**
     * 按可选条件过滤 Agent 列表。任何参数为 null 时忽略该条件。
     * TODO(AM-T6): 会被 Task 6 用带 Testcontainers 测试的实现替换/细化。
     */
    @Query(value = """
            select * from agent a
            where (:category is null or a.category = :category)
              and (:status is null or a.status = :status)
              and (:keyword is null or a.name ilike concat('%', :keyword, '%')
                   or a.description ilike concat('%', :keyword, '%'))
              and (:tag is null or :tag = any(a.tags))
            order by a.created_at desc
            """, nativeQuery = true)
    List<AgentEntity> search(@Param("category") String category,
                              @Param("status") String status,
                              @Param("keyword") String keyword,
                              @Param("tag") String tag);

    @Query(value = "select distinct category from agent where category is not null order by category",
            nativeQuery = true)
    List<String> findDistinctCategories();

    @Query(value = "select distinct t from agent, unnest(tags) as t order by t",
            nativeQuery = true)
    List<String> findDistinctTags();
}
