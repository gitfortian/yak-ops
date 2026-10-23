import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getDataSource, type DataSourceRecord } from "@/service/datasource";
import { getUsersByIds } from "@/service/user";
import { getDataSyncTask, type DataSyncTaskRecord } from "@/service/data-sync";

export function useTaskInformation(taskId?: string) {
  const navigate = useNavigate();
  const basePath = "/data-sync";
  const [task, setTask] = useState<DataSyncTaskRecord>();
  const [taskLoading, setTaskLoading] = useState(true);
  const [dataSources, setDataSources] = useState<{
    source?: DataSourceRecord;
    target?: DataSourceRecord;
  }>({});
  const [updatedBy, setUpdatedBy] = useState("-");
  useEffect(() => {
    if (!taskId) return;
    let disposed = false;
    setTask(undefined);
    setTaskLoading(true);
    void getDataSyncTask(taskId)
      .then((value) => {
        if (disposed) return;
        setTask(value);
      })
      .catch(() => {
        if (!disposed) navigate(basePath, { replace: true });
      })
      .finally(() => {
        if (!disposed) setTaskLoading(false);
      });
    return () => {
      disposed = true;
    };
  }, [basePath, navigate, taskId]);

  useEffect(() => {
    const sourceId = task?.sourceDataSourceId;
    const targetId = task?.targetDataSourceId;
    if (!sourceId || !targetId) {
      setDataSources({});
      return;
    }
    let disposed = false;
    setDataSources({});
    void Promise.all([
      getDataSource(sourceId).catch(() => undefined),
      getDataSource(targetId).catch(() => undefined),
    ]).then(([source, target]) => {
      if (!disposed) setDataSources({ source, target });
    });
    return () => {
      disposed = true;
    };
  }, [task?.sourceDataSourceId, task?.targetDataSourceId]);

  useEffect(() => {
    const userId = task?.updateBy;
    if (!userId || userId === "system") {
      setUpdatedBy(userId === "system" ? "SYSTEM" : "-");
      return;
    }
    let disposed = false;
    setUpdatedBy("-");
    void getUsersByIds([userId])
      .then((users) => {
        if (!disposed) setUpdatedBy(users[0]?.realName || users[0]?.userName || "-");
      })
      .catch(() => {
        if (!disposed) setUpdatedBy("-");
      });
    return () => {
      disposed = true;
    };
  }, [task?.updateBy]);

  return { task, taskLoading, dataSources, updatedBy };
}
