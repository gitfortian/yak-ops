package io.yak.ops.business.lineage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("yakLineageFlyway")
@ConditionalOnDataSourceEnabled
class JdbcLineageRepository implements LineageRepository {

  private static final String ASSET_COLUMNS =
      "SELECT id, asset_key, asset_type, name, source_type, source_id, parent_asset_id, "
          + "data_source_id, database_name, schema_name, table_name, column_name, properties, "
          + "create_time, update_time FROM yak_metadata_asset";

  private static final String RELATION_COLUMNS =
      "SELECT id, source_asset_id, target_asset_id, relation_type, source_type, source_id, "
          + "expression, confidence, version, observed_at, properties, create_time, update_time "
          + "FROM yak_metadata_relation";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  JdbcLineageRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      ObjectMapper objectMapper) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
  }

  @Override
  public LineageAsset upsertAsset(AssetWrite write) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO yak_metadata_asset
            (asset_key, asset_type, name, source_type, source_id, parent_asset_id,
             data_source_id, database_name, schema_name, table_name, column_name, properties,
             create_time, update_time)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
          ON DUPLICATE KEY UPDATE
            asset_type = VALUES(asset_type),
            name = VALUES(name),
            source_type = VALUES(source_type),
            source_id = VALUES(source_id),
            parent_asset_id = VALUES(parent_asset_id),
            data_source_id = VALUES(data_source_id),
            database_name = VALUES(database_name),
            schema_name = VALUES(schema_name),
            table_name = VALUES(table_name),
            column_name = VALUES(column_name),
            properties = VALUES(properties),
            id = LAST_INSERT_ID(id),
            update_time = NOW(6)
          """,
          Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, write.assetKey());
      statement.setString(2, write.assetType().name());
      statement.setString(3, write.name());
      statement.setString(4, write.sourceType());
      statement.setString(5, write.sourceId());
      if (write.parentAssetId() == null) statement.setNull(6, java.sql.Types.BIGINT);
      else statement.setLong(6, write.parentAssetId());
      statement.setString(7, write.dataSourceId());
      statement.setString(8, write.databaseName());
      statement.setString(9, write.schemaName());
      statement.setString(10, write.tableName());
      statement.setString(11, write.columnName());
      statement.setString(12, toJson(write.properties()));
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      return findAssetByKey(write.assetKey())
          .orElseThrow(() -> new IllegalStateException("保存血缘资产后未返回主键"));
    }
    return findAsset(key.longValue())
        .orElseThrow(() -> new IllegalStateException("保存血缘资产后无法读取资产"));
  }

  @Override
  public LineageRelation upsertRelation(RelationWrite write) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO yak_metadata_relation
            (source_asset_id, target_asset_id, relation_type, source_type, source_id,
             expression, confidence, version, observed_at, properties, create_time, update_time)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
          ON DUPLICATE KEY UPDATE
            expression = VALUES(expression),
            confidence = VALUES(confidence),
            observed_at = VALUES(observed_at),
            properties = VALUES(properties),
            id = LAST_INSERT_ID(id),
            update_time = NOW(6)
          """,
          Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, write.sourceAssetId());
      statement.setLong(2, write.targetAssetId());
      statement.setString(3, write.relationType().name());
      statement.setString(4, write.sourceType());
      statement.setString(5, write.sourceId());
      statement.setString(6, write.expression());
      statement.setBigDecimal(7, write.confidence());
      statement.setString(8, write.version());
      statement.setTimestamp(9, Timestamp.from(write.observedAt()));
      statement.setString(10, toJson(write.properties()));
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("保存血缘关系后未返回主键");
    return findRelation(key.longValue());
  }

  @Override
  public Map<String, LineageAsset> upsertAssets(List<AssetWrite> writes, int batchSize) {
    if (writes == null || writes.isEmpty()) return Map.of();
    Map<String, LineageAsset> result = new LinkedHashMap<>();
    for (int start = 0; start < writes.size(); start += batchSize) {
      List<AssetWrite> batch = writes.subList(start, Math.min(start + batchSize, writes.size()));
      jdbcTemplate.batchUpdate(
          """
          INSERT INTO yak_metadata_asset
            (asset_key, asset_type, name, source_type, source_id, parent_asset_id,
             data_source_id, database_name, schema_name, table_name, column_name, properties,
             create_time, update_time)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
          ON DUPLICATE KEY UPDATE
            asset_type=VALUES(asset_type), name=VALUES(name), source_type=VALUES(source_type),
            source_id=VALUES(source_id), parent_asset_id=VALUES(parent_asset_id),
            data_source_id=VALUES(data_source_id), database_name=VALUES(database_name),
            schema_name=VALUES(schema_name), table_name=VALUES(table_name),
            column_name=VALUES(column_name), properties=VALUES(properties), update_time=NOW(6)
          """,
          batch,
          batch.size(),
          (statement, write) -> bindAsset(statement, write));
      List<String> keys = batch.stream().map(AssetWrite::assetKey).toList();
      String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
      for (LineageAsset asset : jdbcTemplate.query(
          ASSET_COLUMNS + " WHERE asset_key IN (" + placeholders + ")", this::mapAsset,
          keys.toArray())) {
        result.put(asset.assetKey(), asset);
      }
    }
    if (result.size() != writes.size()) {
      throw new IllegalStateException("批量保存血缘资产后无法读取全部资产");
    }
    return result;
  }

  static int batchExecutionCount(int itemCount, int batchSize) {
    if (itemCount <= 0) return 0;
    if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
    return (itemCount + batchSize - 1) / batchSize;
  }

  @Override
  public void upsertRelations(List<RelationWrite> writes, int batchSize) {
    if (writes == null || writes.isEmpty()) return;
    for (int start = 0; start < writes.size(); start += batchSize) {
      List<RelationWrite> batch = writes.subList(start, Math.min(start + batchSize, writes.size()));
      jdbcTemplate.batchUpdate(
          """
          INSERT INTO yak_metadata_relation
            (source_asset_id, target_asset_id, relation_type, source_type, source_id,
             expression, confidence, version, observed_at, properties, create_time, update_time)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
          ON DUPLICATE KEY UPDATE expression=VALUES(expression), confidence=VALUES(confidence),
            observed_at=VALUES(observed_at), properties=VALUES(properties), update_time=NOW(6)
          """,
          batch,
          batch.size(),
          (statement, write) -> bindRelation(statement, write));
    }
  }

  @Override
  public int deleteRelationsByEvidence(String sourceType, String sourceId) {
    return jdbcTemplate.update(
        "DELETE FROM yak_metadata_relation WHERE source_type = ? AND source_id = ?",
        sourceType,
        sourceId);
  }

  @Override
  public Set<Long> findAssetIdsByEvidence(String sourceType, String sourceId) {
    return Set.copyOf(jdbcTemplate.queryForList("""
        SELECT source_asset_id AS asset_id FROM yak_metadata_relation
         WHERE source_type = ? AND source_id = ?
        UNION
        SELECT target_asset_id AS asset_id FROM yak_metadata_relation
         WHERE source_type = ? AND source_id = ?
        """, Long.class, sourceType, sourceId, sourceType, sourceId));
  }

  @Override
  public int deleteUnreferencedOwnedAssets(Set<Long> assetIds, String ownerType, String ownerId) {
    if (assetIds == null || assetIds.isEmpty()) return 0;
    String placeholders = String.join(",", Collections.nCopies(assetIds.size(), "?"));
    List<Object> arguments = new ArrayList<>(assetIds);
    arguments.add(ownerType);
    arguments.add(ownerId);
    return jdbcTemplate.update("""
        DELETE asset FROM yak_metadata_asset asset
        LEFT JOIN yak_metadata_relation outgoing ON outgoing.source_asset_id = asset.id
        LEFT JOIN yak_metadata_relation incoming ON incoming.target_asset_id = asset.id
        LEFT JOIN yak_metadata_asset child ON child.parent_asset_id = asset.id
         WHERE asset.id IN (%s)
           AND asset.source_type = ? AND asset.source_id = ?
           AND outgoing.id IS NULL AND incoming.id IS NULL AND child.id IS NULL
        """.formatted(placeholders), arguments.toArray());
  }

  @Override
  public Optional<LineageAsset> lockAssetByKey(String assetKey) {
    return jdbcTemplate.query(
        ASSET_COLUMNS + " WHERE asset_key = ? LIMIT 1 FOR UPDATE", this::mapAsset, assetKey)
        .stream().findFirst();
  }

  @Override
  public Optional<LineageAsset> findAsset(long assetId) {
    return jdbcTemplate.query(
        ASSET_COLUMNS + " WHERE id = ? LIMIT 1", this::mapAsset, assetId)
        .stream().findFirst();
  }

  @Override
  public Optional<LineageAsset> findAssetByKey(String assetKey) {
    return jdbcTemplate.query(
        ASSET_COLUMNS + " WHERE asset_key = ? LIMIT 1", this::mapAsset, assetKey)
        .stream().findFirst();
  }

  @Override
  public List<LineageAsset> searchAssets(String keyword, LineageAssetType assetType, int limit) {
    StringBuilder sql = new StringBuilder(ASSET_COLUMNS).append(" WHERE 1 = 1");
    List<Object> arguments = new ArrayList<>();

    if (keyword != null && !keyword.isBlank()) {
      String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
      sql.append(
          " AND (LOWER(name) LIKE ? OR LOWER(asset_key) LIKE ?"
              + " OR LOWER(COALESCE(table_name, '')) LIKE ?"
              + " OR LOWER(COALESCE(column_name, '')) LIKE ?)");
      arguments.add(pattern);
      arguments.add(pattern);
      arguments.add(pattern);
      arguments.add(pattern);
    }
    if (assetType != null) {
      sql.append(" AND asset_type = ?");
      arguments.add(assetType.name());
    }
    sql.append(" ORDER BY update_time DESC, id DESC LIMIT ?");
    arguments.add(limit);

    return jdbcTemplate.query(sql.toString(), this::mapAsset, arguments.toArray());
  }

  @Override
  public List<LineageAsset> findAssetsByIds(Set<Long> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) return List.of();
    String placeholders = String.join(",", Collections.nCopies(assetIds.size(), "?"));
    return jdbcTemplate.query(
        ASSET_COLUMNS + " WHERE id IN (" + placeholders + ") ORDER BY id ASC",
        this::mapAsset,
        assetIds.toArray());
  }

  @Override
  public List<LineageRelation> findOutgoingRelations(Set<Long> sourceAssetIds) {
    if (sourceAssetIds == null || sourceAssetIds.isEmpty()) return List.of();
    String placeholders = String.join(",", Collections.nCopies(sourceAssetIds.size(), "?"));
    return jdbcTemplate.query(
        RELATION_COLUMNS + " WHERE source_asset_id IN (" + placeholders + ") ORDER BY id ASC",
        this::mapRelation,
        sourceAssetIds.toArray());
  }

  @Override
  public List<LineageRelation> findIncomingRelations(Set<Long> targetAssetIds) {
    if (targetAssetIds == null || targetAssetIds.isEmpty()) return List.of();
    String placeholders = String.join(",", Collections.nCopies(targetAssetIds.size(), "?"));
    return jdbcTemplate.query(
        RELATION_COLUMNS + " WHERE target_asset_id IN (" + placeholders + ") ORDER BY id ASC",
        this::mapRelation,
        targetAssetIds.toArray());
  }

  private LineageRelation findRelation(long relationId) {
    return jdbcTemplate.query(
        RELATION_COLUMNS + " WHERE id = ? LIMIT 1", this::mapRelation, relationId)
        .stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("保存血缘关系后无法读取关系"));
  }

  private void bindAsset(PreparedStatement statement, AssetWrite write) throws SQLException {
    statement.setString(1, write.assetKey());
    statement.setString(2, write.assetType().name());
    statement.setString(3, write.name());
    statement.setString(4, write.sourceType());
    statement.setString(5, write.sourceId());
    if (write.parentAssetId() == null) statement.setNull(6, java.sql.Types.BIGINT);
    else statement.setLong(6, write.parentAssetId());
    statement.setString(7, write.dataSourceId());
    statement.setString(8, write.databaseName());
    statement.setString(9, write.schemaName());
    statement.setString(10, write.tableName());
    statement.setString(11, write.columnName());
    statement.setString(12, toJson(write.properties()));
  }

  private void bindRelation(PreparedStatement statement, RelationWrite write) throws SQLException {
    statement.setLong(1, write.sourceAssetId());
    statement.setLong(2, write.targetAssetId());
    statement.setString(3, write.relationType().name());
    statement.setString(4, write.sourceType());
    statement.setString(5, write.sourceId());
    statement.setString(6, write.expression());
    statement.setBigDecimal(7, write.confidence());
    statement.setString(8, write.version());
    statement.setTimestamp(9, Timestamp.from(write.observedAt()));
    statement.setString(10, toJson(write.properties()));
  }

  private LineageAsset mapAsset(ResultSet rs, int rowNum) throws SQLException {
    long parentValue = rs.getLong("parent_asset_id");
    Long parentAssetId = rs.wasNull() ? null : parentValue;
    return new LineageAsset(
        rs.getLong("id"),
        rs.getString("asset_key"),
        LineageAssetType.valueOf(rs.getString("asset_type")),
        rs.getString("name"),
        rs.getString("source_type"),
        rs.getString("source_id"),
        parentAssetId,
        rs.getString("data_source_id"),
        rs.getString("database_name"),
        rs.getString("schema_name"),
        rs.getString("table_name"),
        rs.getString("column_name"),
        fromJson(rs.getString("properties")),
        instant(rs.getTimestamp("create_time")),
        instant(rs.getTimestamp("update_time")));
  }

  private LineageRelation mapRelation(ResultSet rs, int rowNum) throws SQLException {
    return new LineageRelation(
        rs.getLong("id"),
        rs.getLong("source_asset_id"),
        rs.getLong("target_asset_id"),
        LineageRelationType.valueOf(rs.getString("relation_type")),
        rs.getString("source_type"),
        rs.getString("source_id"),
        rs.getString("expression"),
        rs.getBigDecimal("confidence"),
        rs.getString("version"),
        instant(rs.getTimestamp("observed_at")),
        fromJson(rs.getString("properties")),
        instant(rs.getTimestamp("create_time")),
        instant(rs.getTimestamp("update_time")));
  }

  private String toJson(JsonNode value) {
    if (value == null || value.isNull()) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("血缘 properties 不是有效 JSON", ex);
    }
  }

  private JsonNode fromJson(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("数据库中的血缘 properties 不是有效 JSON", ex);
    }
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
