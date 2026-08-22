package io.yak.ops.business.sync.realtime.dao.mapper;

import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RealtimeJobCommandMapper {
  RealtimeJobDefinitionPO lockDefinition(@Param("id") long id);
  List<Long> lockOtherDesiredRunning(@Param("id") long id);
  int tryAcquireLease(@Param("owner") String owner, @Param("leaseSeconds") int leaseSeconds);
  int reconcileDeployment(
      @Param("deploymentId") long deploymentId,
      @Param("deploymentState") String deploymentState,
      @Param("engineJobId") String engineJobId,
      @Param("error") String error);
}
