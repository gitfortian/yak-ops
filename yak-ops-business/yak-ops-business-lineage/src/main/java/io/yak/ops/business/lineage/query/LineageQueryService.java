package io.yak.ops.business.lineage.query;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable read facade; specialized readers own query implementation details. */
@Service
public class LineageQueryService {

  public static final int MAX_GRAPH_DEPTH = LineageGraphReader.MAX_GRAPH_DEPTH;
  public static final int MAX_ASSET_SEARCH_LIMIT =
      LineageAssetReader.MAX_ASSET_SEARCH_LIMIT;

  private final LineageAssetReader assetReader;
  private final LineageGraphReader graphReader;

  public LineageQueryService(
      LineageAssetReader assetReader, LineageGraphReader graphReader) {
    this.assetReader = assetReader;
    this.graphReader = graphReader;
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageAsset getAsset(long assetId) {
    return assetReader.getAsset(assetId);
  }

  /** Missing-by-key is a normal fallback branch for derived metadata registration. */
  @Transactional(
      value = "yakBusinessTransactionManager",
      readOnly = true,
      noRollbackFor = IllegalArgumentException.class)
  public LineageAsset getAssetByKey(String assetKey) {
    return assetReader.getAssetByKey(assetKey);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public List<LineageAsset> searchAssets(
      String keyword, LineageAssetType assetType, int limit) {
    return assetReader.searchAssets(keyword, assetType, limit);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph upstream(long assetId, int depth) {
    return graphReader.graph(assetId, LineageDirection.UPSTREAM, depth);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph downstream(long assetId, int depth) {
    return graphReader.graph(assetId, LineageDirection.DOWNSTREAM, depth);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph graph(long assetId, LineageDirection direction, int depth) {
    return graphReader.graph(assetId, direction, depth);
  }
}
