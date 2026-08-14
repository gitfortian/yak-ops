package io.yak.ops.business.dataset;

import java.util.List;

/** Runtime strategy per Dataset source type. TABLE/VIEW adapters can be added without changing consumers. */
interface DatasetSourceQueryAdapter {

  DatasetSourceType sourceType();

  DatasetQueryResult execute(
      Dataset dataset,
      DatasetVersion version,
      List<DatasetField> fields,
      DatasetQueryRequest request);
}
