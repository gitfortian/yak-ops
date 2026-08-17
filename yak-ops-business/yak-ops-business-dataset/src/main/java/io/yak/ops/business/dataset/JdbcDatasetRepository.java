package io.yak.ops.business.dataset;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("yakDatasetFlyway")
@ConditionalOnDataSourceEnabled
class JdbcDatasetRepository implements DatasetRepository {

  private static final String DATASET_COLUMNS =
      "SELECT id, name, description, status, current_version_id, create_time, update_time "
          + "FROM yak_dataset";

  private static final String VERSION_COLUMNS =
      "SELECT id, dataset_id, version_no, source_type, source_task_asset_id, "
          + "source_task_revision_id, source_task_revision_no, data_source_id, sql_content, "
          + "schema_snapshot, create_time FROM yak_dataset_version";

  private final JdbcTemplate jdbcTemplate;

  JdbcDatasetRepository(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Override
  public long insertDataset(String name, String description) {
    return insertDatasetInternal(null, name, description);
  }

  @Override
  public long insertDevelopmentNodeDataset(long developmentNodeId, String name, String description) {
    if (developmentNodeId <= 0L) throw new IllegalArgumentException("developmentNodeId 必须大于 0");
    return insertDatasetInternal(developmentNodeId, name, description);
  }

  private long insertDatasetInternal(Long developmentNodeId, String name, String description) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO yak_dataset
            (development_node_id, name, description, status, current_version_id, create_time, update_time)
          VALUES (?, ?, ?, 'ONLINE', NULL, NOW(6), NOW(6))
          """,
          Statement.RETURN_GENERATED_KEYS);
      if (developmentNodeId == null) statement.setNull(1, java.sql.Types.BIGINT);
      else statement.setLong(1, developmentNodeId);
      statement.setString(2, name);
      statement.setString(3, description);
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("创建 Dataset 后未返回主键");
    return key.longValue();
  }

  @Override
  public long insertVersion(
      long datasetId,
      int versionNo,
      DatasetSourceType sourceType,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String schemaSnapshot) {
    return insertVersionInternal(
        datasetId, versionNo, sourceType, sourceTaskAssetId, sourceTaskRevisionId,
        sourceTaskRevisionNo, null, null, schemaSnapshot);
  }

  @Override
  public long insertStandaloneVersion(
      long datasetId,
      int versionNo,
      String dataSourceId,
      String sql,
      String schemaSnapshot) {
    return insertVersionInternal(
        datasetId, versionNo, DatasetSourceType.SQL_QUERY, 0L, 0L, 0,
        dataSourceId, sql, schemaSnapshot);
  }

  private long insertVersionInternal(
      long datasetId,
      int versionNo,
      DatasetSourceType sourceType,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String dataSourceId,
      String sql,
      String schemaSnapshot) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          """
          INSERT INTO yak_dataset_version
            (dataset_id, version_no, source_type, source_task_asset_id,
             source_task_revision_id, source_task_revision_no, data_source_id, sql_content,
             schema_snapshot, create_time)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
          """,
          Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, datasetId);
      statement.setInt(2, versionNo);
      statement.setString(3, sourceType.name());
      statement.setLong(4, sourceTaskAssetId);
      statement.setLong(5, sourceTaskRevisionId);
      statement.setInt(6, sourceTaskRevisionNo);
      statement.setString(7, dataSourceId);
      statement.setString(8, sql);
      statement.setString(9, schemaSnapshot);
      return statement;
    }, keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("创建 DatasetVersion 后未返回主键");
    return key.longValue();
  }

  @Override
  public void insertFields(long versionId, List<DatasetService.FieldSpec> fields) {
    for (int index = 0; index < fields.size(); index++) {
      DatasetService.FieldSpec field = fields.get(index);
      jdbcTemplate.update(
          """
          INSERT INTO yak_dataset_field
            (field_id, version_id, physical_name, display_name, data_type, nullable,
             description, default_role, sort_order)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          field.fieldId(), versionId, field.physicalName(), field.displayName(),
          field.dataType().name(), field.nullable(), field.description(),
          field.defaultRole().name(), index + 1);
    }
  }

  @Override
  public void updateCurrentVersion(long datasetId, long versionId) {
    int updated = jdbcTemplate.update(
        "UPDATE yak_dataset SET current_version_id = ?, update_time = NOW(6) WHERE id = ?",
        versionId, datasetId);
    if (updated != 1) throw new IllegalArgumentException("Dataset 不存在：" + datasetId);
  }

  @Override
  public void updateStatus(long datasetId, DatasetStatus status) {
    int updated = jdbcTemplate.update(
        "UPDATE yak_dataset SET status = ?, update_time = NOW(6) WHERE id = ?",
        status.name(), datasetId);
    if (updated != 1) throw new IllegalArgumentException("Dataset 不存在：" + datasetId);
  }

  @Override
  public void updateMetadata(long datasetId, String name, String description) {
    int updated = jdbcTemplate.update(
        "UPDATE yak_dataset SET name = ?, description = ?, update_time = NOW(6) WHERE id = ?",
        name, description, datasetId);
    if (updated != 1) throw new IllegalArgumentException("Dataset 不存在：" + datasetId);
  }

  @Override
  public Optional<Dataset> findDataset(long datasetId) {
    return jdbcTemplate.query(
        DATASET_COLUMNS + " WHERE id = ? LIMIT 1", JdbcDatasetRepository::mapDataset, datasetId)
        .stream().findFirst();
  }

  @Override
  public Optional<Dataset> findDatasetBySourceTaskAssetId(long sourceTaskAssetId) {
    return jdbcTemplate.query(
        """
        SELECT DISTINCT d.id, d.name, d.description, d.status, d.current_version_id,
               d.create_time, d.update_time
        FROM yak_dataset d
        INNER JOIN yak_dataset_version v ON v.dataset_id = d.id
        WHERE v.source_task_asset_id = ?
          AND d.development_node_id IS NULL
        ORDER BY d.update_time DESC, d.id DESC
        LIMIT 1
        """,
        JdbcDatasetRepository::mapDataset, sourceTaskAssetId).stream().findFirst();
  }

  @Override
  public Optional<Dataset> findDatasetByDevelopmentNodeId(long developmentNodeId) {
    return jdbcTemplate.query(
        DATASET_COLUMNS + " WHERE development_node_id = ? LIMIT 1",
        JdbcDatasetRepository::mapDataset, developmentNodeId).stream().findFirst();
  }

  @Override
  public List<Dataset> listDatasets() {
    return jdbcTemplate.query(
        DATASET_COLUMNS + " ORDER BY update_time DESC, id DESC", JdbcDatasetRepository::mapDataset);
  }

  @Override
  public Optional<DatasetVersion> findVersion(long versionId) {
    return jdbcTemplate.query(
        VERSION_COLUMNS + " WHERE id = ? LIMIT 1", JdbcDatasetRepository::mapVersion, versionId)
        .stream().findFirst();
  }

  @Override
  public List<DatasetVersion> listVersions(long datasetId) {
    return jdbcTemplate.query(
        VERSION_COLUMNS + " WHERE dataset_id = ? ORDER BY version_no DESC",
        JdbcDatasetRepository::mapVersion, datasetId);
  }

  @Override
  public List<DatasetField> listFields(long versionId) {
    return jdbcTemplate.query(
        """
        SELECT field_id, version_id, physical_name, display_name, data_type, nullable,
               description, default_role, sort_order
        FROM yak_dataset_field
        WHERE version_id = ?
        ORDER BY sort_order ASC, physical_name ASC
        """,
        JdbcDatasetRepository::mapField, versionId);
  }

  @Override
  public int nextVersionNo(long datasetId) {
    Integer value = jdbcTemplate.queryForObject(
        "SELECT COALESCE(MAX(version_no), 0) + 1 FROM yak_dataset_version WHERE dataset_id = ?",
        Integer.class, datasetId);
    return value == null ? 1 : value;
  }

  private static Dataset mapDataset(ResultSet rs, int rowNum) throws SQLException {
    Object currentValue = rs.getObject("current_version_id");
    Long currentVersionId = currentValue == null ? null : rs.getLong("current_version_id");
    return new Dataset(
        rs.getLong("id"), rs.getString("name"), rs.getString("description"),
        DatasetStatus.valueOf(rs.getString("status")), currentVersionId,
        instant(rs.getTimestamp("create_time")), instant(rs.getTimestamp("update_time")));
  }

  private static DatasetVersion mapVersion(ResultSet rs, int rowNum) throws SQLException {
    return new DatasetVersion(
        rs.getLong("id"),
        rs.getLong("dataset_id"),
        rs.getInt("version_no"),
        DatasetSourceType.valueOf(rs.getString("source_type")),
        rs.getLong("source_task_asset_id"),
        rs.getLong("source_task_revision_id"),
        rs.getInt("source_task_revision_no"),
        rs.getString("data_source_id"),
        rs.getString("sql_content"),
        rs.getString("schema_snapshot"),
        instant(rs.getTimestamp("create_time")));
  }

  private static DatasetField mapField(ResultSet rs, int rowNum) throws SQLException {
    return new DatasetField(
        rs.getString("field_id"), rs.getLong("version_id"), rs.getString("physical_name"),
        rs.getString("display_name"), DatasetFieldDataType.valueOf(rs.getString("data_type")),
        rs.getBoolean("nullable"), rs.getString("description"),
        DatasetFieldRole.valueOf(rs.getString("default_role")), rs.getInt("sort_order"));
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
