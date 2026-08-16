import { history } from '@umijs/max';
import { useEffect } from 'react';
import type { DataServiceApi, DataSourceOption } from './service';

interface DataServiceDetailDrawerProps {
  open: boolean;
  service?: DataServiceApi;
  dataSources: DataSourceOption[];
  onClose: () => void;
  onChanged: () => Promise<void> | void;
}

/**
 * 保留原调用入口作为轻量导航桥接，API 详情已迁移到独立页面。
 */
export default function DataServiceDetailDrawer({
  open,
  service,
  onClose,
}: DataServiceDetailDrawerProps) {
  useEffect(() => {
    if (!open || !service?.id) return;
    onClose();
    history.push(`/data-service/api/${encodeURIComponent(String(service.id))}`);
  }, [onClose, open, service?.id]);

  return null;
}
