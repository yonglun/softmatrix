package com.softmatrix.portal.org;

import com.softmatrix.portal.common.ApiException;
import com.softmatrix.portal.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PositionServiceTest {

    PositionRepository repo = mock(PositionRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    PositionService service;

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PositionService(repo, users);
    }

    @Test
    void create_duplicate_name_rejected() {
        when(repo.existsByName("工程师")).thenReturn(true);
        assertThatThrownBy(() -> service.create("工程师"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "POSITION_NAME_TAKEN");
    }

    @Test
    void delete_in_use_rejected() {
        UUID id = UUID.randomUUID();
        PositionEntity p = new PositionEntity();
        p.setId(id);
        when(repo.findById(id)).thenReturn(Optional.of(p));
        when(users.countByPositionId(id)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "POSITION_IN_USE");
    }
}
