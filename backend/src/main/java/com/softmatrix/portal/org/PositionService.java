package com.softmatrix.portal.org;

import com.softmatrix.portal.common.ApiException;
import com.softmatrix.portal.org.dto.PositionResponse;
import com.softmatrix.portal.user.AppUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PositionService {

    private final PositionRepository repo;
    private final AppUserRepository users;

    public PositionService(PositionRepository repo, AppUserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    public List<PositionResponse> list() {
        return repo.findAll(Sort.by("name")).stream()
                .map(p -> new PositionResponse(p.getId(), p.getName())).toList();
    }

    public PositionResponse create(String name) {
        if (repo.existsByName(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "POSITION_NAME_TAKEN", "岗位名称已存在");
        }
        PositionEntity p = new PositionEntity();
        p.setName(name);
        p = repo.save(p);
        return new PositionResponse(p.getId(), p.getName());
    }

    public void delete(UUID id) {
        PositionEntity p = repo.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "POSITION_NOT_FOUND", "岗位不存在"));
        if (users.countByPositionId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "POSITION_IN_USE", "该岗位仍有用户在用");
        }
        repo.delete(p);
    }
}
