package com.softmatrix.portal.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {
    Optional<AppUserEntity> findByUsername(String username);
    List<AppUserEntity> findByDepartmentIdOrderByUsername(UUID departmentId);
    List<AppUserEntity> findAllByOrderByUsername();
    long countByDepartmentId(UUID departmentId);
    long countByPositionId(UUID positionId);
    long countByRoles_Id(UUID roleId);
}
