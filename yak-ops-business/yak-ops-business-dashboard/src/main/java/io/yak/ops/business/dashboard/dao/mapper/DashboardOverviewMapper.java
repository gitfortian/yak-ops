package io.yak.ops.business.dashboard.dao.mapper;

import io.yak.ops.business.dashboard.dao.model.DashboardOverviewSummaryPO;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Bounded SQL projections for Dashboard overview surfaces. */
@Mapper
public interface DashboardOverviewMapper {

  @Select("""
      SELECT
          COUNT(*) AS dashboardCount,
          COALESCE(SUM(CASE
              WHEN published_version_id IS NOT NULL THEN 1
              ELSE 0
          END), 0) AS publishedDashboardCount
      FROM yak_dashboard
      """)
  DashboardOverviewSummaryPO selectSummary();

  @Select("""
      SELECT
          id,
          name,
          description,
          current_version_id AS currentVersionId,
          current_version_no AS currentVersionNo,
          published_version_id AS publishedVersionId,
          published_version_no AS publishedVersionNo,
          published_time AS publishedTime,
          create_time AS createTime,
          update_time AS updateTime
      FROM yak_dashboard
      ORDER BY update_time DESC, id DESC
      LIMIT #{limit}
      """)
  List<DashboardPO> selectRecent(@Param("limit") int limit);
}
