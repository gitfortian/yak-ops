package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.dao.entity.modeling.MetadataCollectionEntity;
import io.yak.ops.dao.entity.modeling.MetadataFieldEntity;
import io.yak.ops.dao.entity.modeling.MetadataTableEntity;
import io.yak.ops.dao.mapper.modeling.MetadataMapper;
import io.yak.ops.dao.repository.modeling.MetadataRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes metadata snapshot statements inside the owning business transaction. */
@Repository
public class MetadataRepositoryImpl implements MetadataRepository {
    @Resource
    private MetadataMapper mapper;

    @Override
    public void addCollection(MetadataCollectionEntity row) {
        mapper.insertCollection(row);
    }

    @Override
    public void addTable(MetadataTableEntity row) {
        mapper.insertTable(row);
    }

    @Override
    public void addField(MetadataFieldEntity row) {
        mapper.insertField(row);
    }

    @Override
    public Optional<MetadataCollectionEntity> latestCollection(String workspaceId, String publicationId) {
        return Optional.ofNullable(mapper.latestCollection(workspaceId, publicationId));
    }

    @Override
    public List<MetadataTableEntity> tables(String workspaceId, String collectionId) {
        return mapper.listTables(workspaceId, collectionId);
    }

    @Override
    public List<MetadataFieldEntity> fields(String workspaceId, String tableId) {
        return mapper.listFields(workspaceId, tableId);
    }

    @Override
    public void deleteByPublication(String workspaceId, String publicationId) {
        mapper.deleteByPublication(workspaceId, publicationId);
    }
}
