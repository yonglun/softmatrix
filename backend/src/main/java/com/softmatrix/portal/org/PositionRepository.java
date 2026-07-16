package com.softmatrix.portal.org;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<PositionEntity, UUID> {
    boolean existsByName(String name);
}
