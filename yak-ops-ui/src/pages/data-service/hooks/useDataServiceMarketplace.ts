import {
  deleteDataService,
  listDataServiceDataSources,
  listDataServices,
  listRecentDataServiceLogs,
  setDataServiceEnabled,
  type DataServiceApi,
  type DataServiceCallLog,
  type DataSourceOption,
} from '@/services/data-service';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  buildDataServiceCallCounts,
  buildDataSourceNameMap,
  copyDataServiceText,
  filterDataServices,
  resolveDataSourceName,
  selectHotDataServices,
  selectRecommendedDataServices,
  selectRunningDataServices,
} from '../utils';

export const useDataServiceMarketplace = () => {
  const requestSequenceRef = useRef(0);
  const [services, setServices] = useState<DataServiceApi[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [logs, setLogs] = useState<DataServiceCallLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [detailTarget, setDetailTarget] = useState<DataServiceApi>();

  const loadMarketplace = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setLoading(true);

    try {
      const [serviceResult, dataSourceResult, logResult] = await Promise.all([
        listDataServices(),
        listDataServiceDataSources(),
        listRecentDataServiceLogs(),
      ]);
      if (requestSequence !== requestSequenceRef.current) return;

      const nextServices = serviceResult || [];
      setServices(nextServices);
      setDataSources(dataSourceResult || []);
      setLogs(logResult || []);
      setDetailTarget((current) =>
        current
          ? nextServices.find((service) => service.id === current.id)
          : undefined,
      );
    } catch (error) {
      if (requestSequence === requestSequenceRef.current) {
        message.error(
          error instanceof Error ? error.message : '加载 API 集市失败',
        );
      }
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadMarketplace();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadMarketplace]);

  const dataSourceNameMap = useMemo(
    () => buildDataSourceNameMap(dataSources),
    [dataSources],
  );
  const callsByApiId = useMemo(
    () => buildDataServiceCallCounts(logs),
    [logs],
  );
  const runningServices = useMemo(
    () => selectRunningDataServices(services),
    [services],
  );
  const recommendedServices = useMemo(
    () => selectRecommendedDataServices(services),
    [services],
  );
  const hotServices = useMemo(
    () => selectHotDataServices(services, callsByApiId),
    [callsByApiId, services],
  );
  const searchResults = useMemo(
    () =>
      filterDataServices(
        services,
        submittedKeyword,
        dataSourceNameMap,
      ),
    [dataSourceNameMap, services, submittedKeyword],
  );

  const dataSourceName = useCallback(
    (dataSourceId?: number) =>
      resolveDataSourceName(dataSourceNameMap, dataSourceId),
    [dataSourceNameMap],
  );

  const changeKeyword = useCallback((value: string) => {
    setKeyword(value);
    if (!value) setSubmittedKeyword('');
  }, []);

  const search = useCallback(() => {
    setSubmittedKeyword(keyword.trim());
  }, [keyword]);

  const resetSearch = useCallback(() => {
    setKeyword('');
    setSubmittedKeyword('');
  }, []);

  const openDetail = useCallback((service: DataServiceApi) => {
    setDetailTarget(service);
  }, []);

  const closeDetail = useCallback(() => {
    setDetailTarget(undefined);
  }, []);

  const deleteService = useCallback(
    async (service: DataServiceApi) => {
      try {
        await deleteDataService(service.id);
        setDetailTarget((current) =>
          current?.id === service.id ? undefined : current,
        );
        message.success('API 已删除');
        await loadMarketplace();
      } catch (error) {
        message.error(error instanceof Error ? error.message : '删除失败');
        throw error;
      }
    },
    [loadMarketplace],
  );

  const toggleService = useCallback(
    async (service: DataServiceApi, enabled: boolean) => {
      try {
        await setDataServiceEnabled(service.id, enabled);
        message.success(enabled ? 'API 已启用' : 'API 已停用');
        await loadMarketplace();
      } catch (error) {
        message.error(
          error instanceof Error ? error.message : '状态更新失败',
        );
      }
    },
    [loadMarketplace],
  );

  const copyEndpoint = useCallback(async (endpoint: string) => {
    try {
      await copyDataServiceText(endpoint);
      message.success('Endpoint 已复制');
    } catch {
      message.warning('复制失败，请手动复制');
    }
  }, []);

  return {
    services,
    loading,
    keyword,
    submittedKeyword,
    detailTarget,
    callsByApiId,
    runningServices,
    recommendedServices,
    hotServices,
    searchResults,
    searching: Boolean(submittedKeyword.trim()),
    totalCalls: logs.length,
    dataSourceName,
    changeKeyword,
    search,
    resetSearch,
    openDetail,
    closeDetail,
    deleteService,
    toggleService,
    copyEndpoint,
  };
};
