package io.yak.ops.dao.mapper.modeling;

import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Parameter-bound statements for immutable publication records.
 *
 * <p>Records are inserted once and only ever transition from {@code PENDING} to a terminal state, so
 * there is deliberately no general update statement: every write goes through one of the explicit
 * transitions below.
 */
@Mapper
public interface ModelPublicationMapper {
    String PROJECTION = "SELECT p.id,p.workspace_id,p.model_id,p.datasource_id,p.datasource_revision,"
            + "p.physical_database,p.physical_schema,p.physical_table,p.status,p.ddl,p.error,"
            + "p.created_time,p.finished_time,p.create_by "
            + "FROM yak_ops_modeling_model_publication p ";

    @Insert("INSERT INTO yak_ops_modeling_model_publication "
            + "(id,workspace_id,model_id,datasource_id,datasource_revision,physical_database,physical_schema,"
            + "physical_table,status,ddl,error,created_time,finished_time,create_by) VALUES "
            + "(#{id},#{workspaceId},#{modelId},#{datasourceId},#{datasourceRevision},#{physicalDatabase},"
            + "#{physicalSchema},#{physicalTable},#{status},#{ddl},#{error},#{createdTime},#{finishedTime},"
            + "#{createBy})")
    int insert(ModelPublicationEntity row);

    @Select(PROJECTION + "WHERE p.workspace_id=#{workspaceId} AND p.id=#{id}")
    ModelPublicationEntity find(@Param("workspaceId") String workspaceId, @Param("id") String id);

    @Select(PROJECTION + "WHERE p.workspace_id=#{workspaceId} AND p.model_id=#{modelId} "
            + "ORDER BY p.created_time DESC, p.id DESC LIMIT #{limit}")
    List<ModelPublicationEntity> listByModel(
            @Param("workspaceId") String workspaceId, @Param("modelId") String modelId, @Param("limit") int limit);

    @Update("UPDATE yak_ops_modeling_model_publication SET status=#{status},error=#{error},"
            + "finished_time=#{finishedTime} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='PENDING'")
    int finish(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("status") String status,
            @Param("error") String error,
            @Param("finishedTime") LocalDateTime finishedTime);

    @Update("UPDATE yak_ops_modeling_model_publication SET status=#{status},error=#{error},"
            + "finished_time=#{finishedTime} "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='UNKNOWN'")
    int reconcile(
            @Param("workspaceId") String workspaceId,
            @Param("id") String id,
            @Param("status") String status,
            @Param("error") String error,
            @Param("finishedTime") LocalDateTime finishedTime);

    @Select("SELECT COUNT(*) FROM yak_ops_modeling_model_publication "
            + "WHERE workspace_id=#{workspaceId} AND id=#{id}")
    long count(@Param("workspaceId") String workspaceId, @Param("id") String id);

    /**
     * Reads the newest record of each requested model in one round trip.
     *
     * <p>Only {@code model_id} and {@code status} are selected, so the returned rows carry every
     * other field as null on purpose: this is a status projection for a model list, not a record
     * read. Rows arrive newest first, so the caller keeps the first status it sees per model even
     * when two publications share a timestamp.
     *
     * @param workspaceId authorized Workspace
     * @param modelIds model identities, never empty
     * @return one row per publication of those models, newest first
     */
    @Select({
        "<script>",
        "SELECT p.model_id, p.status FROM yak_ops_modeling_model_publication p ",
        "WHERE p.workspace_id=#{workspaceId} AND p.model_id IN ",
        "<foreach collection='modelIds' item='modelId' open='(' separator=',' close=')'>#{modelId}</foreach>",
        " ORDER BY p.created_time DESC, p.id DESC",
        "</script>"
    })
    List<ModelPublicationEntity> latestStatusByModel(
            @Param("workspaceId") String workspaceId, @Param("modelIds") Collection<String> modelIds);
}
