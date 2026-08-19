package io.yak.ops.business.dataset;

import java.util.List;

/** Runtime strategy per Dataset source type. TABLE/VIEW adapters can be added without changing consumers. */
interface DatasetSourceQueryAdapter {

  DatasetSourceType sourceType();

  DatasetQueryExecution execute(
      Dataset dataset,
      DatasetVersion version,
      List<DatasetField> fields,
      DatasetQueryRequest request);
}

/** Adapter result plus execution-only diagnostics used by the Dataset performance analyzer. */
record DatasetQueryExecution(
    DatasetQueryResult result,
    String dataSourceId,
    String sql,
    long prepareMillis,
    long waitMillis,
    long executeMillis,
    long transferMillis) {
}
