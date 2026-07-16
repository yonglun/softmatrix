package com.softmatrix.portal.org;

import com.softmatrix.portal.common.ApiException;
import com.softmatrix.portal.org.dto.DepartmentNode;
import com.softmatrix.portal.org.dto.DepartmentRequest;
import com.softmatrix.portal.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DepartmentServiceTest {

    DepartmentRepository repo = mock(DepartmentRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    DepartmentService service;

    UUID rootId = UUID.randomUUID();
    UUID childId = UUID.randomUUID();
    DepartmentEntity root = dept(null, "总部");
    DepartmentEntity child = dept(rootId, "研发");

    private DepartmentEntity dept(UUID parentId, String name) {
        DepartmentEntity d = new DepartmentEntity();
        d.setName(name);
        d.setParentId(parentId);
        return d;
    }

    @BeforeEach
    void setUp() {
        root.setId(rootId);
        child.setId(childId);
        when(repo.findById(rootId)).thenReturn(Optional.of(root));
        when(repo.findById(childId)).thenReturn(Optional.of(child));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(users.findAllById(any())).thenReturn(List.of());
        service = new DepartmentService(repo, users);
    }

    @Test
    void tree_assembles_children_under_root() {
        when(repo.findAll()).thenReturn(List.of(root, child));

        List<DepartmentNode> tree = service.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).children()).extracting(DepartmentNode::name).containsExactly("研发");
    }

    @Test
    void create_requires_existing_parent() {
        UUID ghost = UUID.randomUUID();
        when(repo.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new DepartmentRequest("新部门", ghost, null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "DEPT_NOT_FOUND");
    }

    @Test
    void root_cannot_be_given_a_parent() {
        assertThatThrownBy(() -> service.update(rootId, new DepartmentRequest("总部", childId, null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "DEPT_ROOT");
    }

    @Test
    void move_under_own_descendant_rejected() {
        // 把根下的 child 移到 child 自己的子部门 grand 之下 → 环
        DepartmentEntity grand = dept(childId, "孙部门");
        UUID grandId = UUID.randomUUID();
        grand.setId(grandId);
        when(repo.findById(grandId)).thenReturn(Optional.of(grand));

        assertThatThrownBy(() -> service.update(childId, new DepartmentRequest("研发", grandId, null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "DEPT_CYCLE");
    }

    @Test
    void delete_root_rejected() {
        assertThatThrownBy(() -> service.delete(rootId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "DEPT_ROOT");
    }

    @Test
    void delete_with_children_or_users_rejected() {
        when(repo.existsByParentId(childId)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(childId))
                .hasFieldOrPropertyWithValue("code", "DEPT_NOT_EMPTY");

        when(repo.existsByParentId(childId)).thenReturn(false);
        when(users.countByDepartmentId(childId)).thenReturn(2L);
        assertThatThrownBy(() -> service.delete(childId))
                .hasFieldOrPropertyWithValue("code", "DEPT_NOT_EMPTY");
    }

    @Test
    void delete_empty_leaf_ok() {
        when(repo.existsByParentId(childId)).thenReturn(false);
        when(users.countByDepartmentId(childId)).thenReturn(0L);

        service.delete(childId);

        verify(repo).delete(child);
    }
}
