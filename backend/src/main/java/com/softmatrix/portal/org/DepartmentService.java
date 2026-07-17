package com.softmatrix.portal.org;

import com.softmatrix.portal.common.ApiException;
import com.softmatrix.portal.org.dto.DepartmentNode;
import com.softmatrix.portal.org.dto.DepartmentRequest;
import com.softmatrix.portal.user.AppUserEntity;
import com.softmatrix.portal.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository repo;
    private final AppUserRepository users;

    public DepartmentService(DepartmentRepository repo, AppUserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    /** 全量加载组树;负责人姓名一次性批量解析。 */
    public List<DepartmentNode> tree() {
        List<DepartmentEntity> all = repo.findAll();
        Set<UUID> managerIds = all.stream().map(DepartmentEntity::getManagerUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> managerNames = users.findAllById(managerIds).stream()
                .collect(Collectors.toMap(AppUserEntity::getId,
                        u -> u.getName() == null ? u.getUsername() : u.getName()));
        Map<UUID, List<DepartmentEntity>> byParent = all.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(DepartmentEntity::getParentId));
        Function<DepartmentEntity, DepartmentNode> toNode = new Function<>() {
            @Override
            public DepartmentNode apply(DepartmentEntity d) {
                List<DepartmentNode> children = byParent.getOrDefault(d.getId(), List.of())
                        .stream().map(this).toList();
                return new DepartmentNode(d.getId(), d.getName(), d.getParentId(),
                        d.getManagerUserId(), managerNames.get(d.getManagerUserId()), children);
            }
        };
        return all.stream().filter(d -> d.getParentId() == null).map(toNode).toList();
    }

    public DepartmentNode create(DepartmentRequest req) {
        if (req.parentId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "DEPT_ROOT", "根部门唯一,新部门必须有父部门");
        }
        find(req.parentId());
        DepartmentEntity d = new DepartmentEntity();
        d.setName(req.name());
        d.setParentId(req.parentId());
        d.setManagerUserId(req.managerUserId());
        return leaf(repo.save(d));
    }

    public DepartmentNode update(UUID id, DepartmentRequest req) {
        DepartmentEntity d = find(id);
        if (d.getParentId() == null) {
            if (req.parentId() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "DEPT_ROOT", "根部门不可移动");
            }
        } else {
            if (req.parentId() == null) {
                throw new ApiException(HttpStatus.CONFLICT, "DEPT_ROOT", "根部门唯一,不可将部门提升为根");
            }
            find(req.parentId());
            assertNoCycle(id, req.parentId());
            d.setParentId(req.parentId());
        }
        d.setName(req.name());
        d.setManagerUserId(req.managerUserId());
        return leaf(repo.save(d));
    }

    public void delete(UUID id) {
        DepartmentEntity d = find(id);
        if (d.getParentId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "DEPT_ROOT", "根部门不可删除");
        }
        if (repo.existsByParentId(id) || users.countByDepartmentId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DEPT_NOT_EMPTY", "请先清空子部门与部门成员");
        }
        repo.delete(d);
    }

    private void assertNoCycle(UUID id, UUID newParentId) {
        UUID cursor = newParentId;
        while (cursor != null) {
            if (cursor.equals(id)) {
                throw new ApiException(HttpStatus.CONFLICT, "DEPT_CYCLE", "不能移动到自己的子部门下");
            }
            cursor = find(cursor).getParentId();
        }
    }

    private DepartmentEntity find(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "DEPT_NOT_FOUND", "部门不存在"));
    }

    private DepartmentNode leaf(DepartmentEntity d) {
        return new DepartmentNode(d.getId(), d.getName(), d.getParentId(),
                d.getManagerUserId(), null, List.of());
    }
}
