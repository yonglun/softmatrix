package com.softmatrix.portal.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {

    @Query(value = """
            SELECT * FROM agent
            WHERE (:category IS NULL OR category = :category)
              AND (:status   IS NULL OR status = :status)
              AND (:keyword  IS NULL OR name ILIKE '%' || :keyword || '%')
              AND (:tag      IS NULL OR :tag = ANY(tags))
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<AgentEntity> search(@Param("category") String category,
                             @Param("status") String status,
                             @Param("keyword") String keyword,
                             @Param("tag") String tag);

    @Query(value = "SELECT DISTINCT category FROM agent WHERE category IS NOT NULL ORDER BY category",
            nativeQuery = true)
    List<String> findDistinctCategories();

    @Query(value = "SELECT DISTINCT unnest(tags) AS tag FROM agent ORDER BY tag", nativeQuery = true)
    List<String> findDistinctTags();
}
