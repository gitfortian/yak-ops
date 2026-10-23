import { useCallback, useEffect, useRef, useState, type MutableRefObject } from "react";
import { listDataSources, type DataSourceRecord } from "@/service/datasource";
import type { DataSyncTaskRecord } from "@/service/data-sync";

export function useEditorDataSources(
  connections: DataSourceRecord[],
  savedTask: MutableRefObject<DataSyncTaskRecord | undefined>,
) {
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>(connections);
  const [dataSourcesLoading, setDataSourcesLoading] = useState(false);

  const datasourceRequest = useRef<AbortController>();
  const loadDataSources = useCallback(async () => {
    datasourceRequest.current?.abort();
    const controller = new AbortController();
    datasourceRequest.current = controller;
    setDataSourcesLoading(true);
    try {
      const result = await listDataSources({ pageNo: 1, pageSize: 200 }, controller.signal);
      if (controller.signal.aborted) return;
      setDataSources((current) => {
        const selected = current.filter(
          (value) =>
            value.id === savedTask.current?.sourceDataSourceId ||
            value.id === savedTask.current?.targetDataSourceId,
        );
        return [
          ...new Map([...selected, ...result.bizData].map((value) => [value.id, value])).values(),
        ];
      });
    } catch {
      // HTTP failures are reported by the shared transport; existing selections are retained.
    } finally {
      if (!controller.signal.aborted) setDataSourcesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDataSources();
    return () => datasourceRequest.current?.abort();
  }, [loadDataSources]);

  return { dataSources, dataSourcesLoading, loadDataSources };
}
