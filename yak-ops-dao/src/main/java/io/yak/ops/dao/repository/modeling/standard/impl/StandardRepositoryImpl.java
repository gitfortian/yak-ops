package io.yak.ops.dao.repository.modeling.standard.impl;

import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardFieldEntity;
import io.yak.ops.dao.mapper.modeling.standard.StandardMapper;
import io.yak.ops.dao.repository.modeling.standard.FieldQuery;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes bound persistence operations in the calling business transaction. */
@Repository
public class StandardRepositoryImpl implements StandardRepository {
    @Resource
    private StandardMapper mapper;

    private static String normalize(String actor) {
        return actor == null || actor.isBlank() ? CommonConstants.SYSTEM_USER : actor.strip();
    }

    @Override
    public boolean lockCatalog(String workspaceId, String actor) {
        Boolean initialized = mapper.lockCatalog(workspaceId);
        if (initialized == null) {
            // Concurrent first writers block on the reservation; only the winner commits the defaults marker.
            mapper.reserveCatalog(workspaceId, normalize(actor), DateUtils.now());
            initialized = mapper.lockCatalog(workspaceId);
        }
        return Boolean.TRUE.equals(initialized);
    }

    @Override
    public void markInitialized(String workspaceId) {
        mapper.markInitialized(workspaceId, DateUtils.now());
    }

    @Override
    public List<CategoryEntity> categories(String workspaceId) {
        return mapper.categories(workspaceId);
    }

    @Override
    public Optional<CategoryEntity> category(String workspaceId, String id) {
        return Optional.ofNullable(mapper.category(workspaceId, id));
    }

    @Override
    public void insertCategory(CategoryEntity node) {
        mapper.insertCategory(node);
    }

    @Override
    public boolean updateCategory(CategoryEntity node) {
        return mapper.updateCategory(node) == 1;
    }

    @Override
    public boolean deleteCategory(String workspaceId, String id, long revision) {
        return mapper.deleteCategory(workspaceId, id, revision) == 1;
    }

    @Override
    public boolean hasFields(String workspaceId, String categoryId) {
        return mapper.fieldsInCategory(workspaceId, categoryId) > 0;
    }

    @Override
    public Optional<StandardFieldEntity> field(String workspaceId, String id) {
        return Optional.ofNullable(mapper.field(workspaceId, id));
    }

    @Override
    public void insertField(StandardFieldEntity node) {
        mapper.insertField(node);
    }

    @Override
    public boolean updateField(StandardFieldEntity node) {
        return mapper.updateField(node) == 1;
    }

    @Override
    public boolean deleteField(String workspaceId, String id, long revision) {
        return mapper.deleteField(workspaceId, id, revision) == 1;
    }

    @Override
    public boolean occupied(String enCode, String excludeId) {
        return mapper.occupied(enCode, excludeId) > 0;
    }

    @Override
    public List<StandardFieldEntity> fields(String workspaceId, FieldQuery query) {
        return mapper.fields(workspaceId, query);
    }

    @Override
    public long countFields(String workspaceId, FieldQuery query) {
        return mapper.countFields(workspaceId, query);
    }
}
