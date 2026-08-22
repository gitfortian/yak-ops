package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobListQuery;
import org.springframework.stereotype.Service;

/** Application read boundary for realtime job list queries. */
@Service
public class RealtimeJobQueryService {

  private final RealtimeJobListQuery query;

  public RealtimeJobQueryService(RealtimeJobListQuery query) {
    this.query = query;
  }

  public RealtimeJobPage page(
      int pageNo,
      int pageSize,
      String keyword,
      Long id,
      String releaseState,
      String stateGroup) {
    return query.page(pageNo, pageSize, keyword, id, releaseState, stateGroup);
  }
}
