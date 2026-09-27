import { Badge, Button, PageHeader } from "@yak-ops/yak-ui";
import { ArrowRight } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import {
  cancelDataSyncInstance,
  getDataSyncInstance,
  type DataSyncInstanceRecord,
} from "@/service/data-sync";

import { statusMeta } from "./instances";

const pathText = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

export function OfflineSyncInstanceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [record, setRecord] = useState<DataSyncInstanceRecord>();
  const [loading, setLoading] = useState(true);
  const [canceling, setCanceling] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    const value = await getDataSyncInstance(id);
    setRecord(value);
    setLoading(false);
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!record || (record.status !== "PENDING" && record.status !== "RUNNING")) return;
    const timer = window.setInterval(() => void load(), 1000);
    return () => window.clearInterval(timer);
  }, [load, record]);

  const cancel = async () => {
    if (!record || canceling) return;
    setCanceling(true);
    try {
      setRecord(await cancelDataSyncInstance(record.id));
    } finally {
      setCanceling(false);
    }
  };

  if (loading || !record) {
    return <div className="p-8 text-sm text-[#667085]">正在加载同步实例...</div>;
  }

  const meta = statusMeta(record.status);
  const snapshot = record.definitionSnapshot;

  return (
    <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
      <PageHeader
        title={`${record.taskName} / ${record.id}`}
        description={`任务版本 v${record.taskVersion} · ${record.triggerType === "MANUAL" ? "手动运行" : record.triggerType}`}
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <>
            {(record.status === "PENDING" || record.status === "RUNNING") && (
              <Button
                size="small"
                variant="danger"
                loading={canceling}
                onClick={() => void cancel()}
              >
                停止
              </Button>
            )}
            <Button size="small" onClick={() => navigate("/offline-sync?tab=instances")}>
              返回实例列表
            </Button>
          </>
        }
      />

      <div className="space-y-4 px-6 pb-8 pt-5 max-md:px-4">
        <section className="rounded-lg border border-[#e6e8eb] bg-white p-5">
          <div className="flex flex-wrap items-center gap-3">
            <Badge tone={meta.tone}>{meta.label}</Badge>
            <span className="text-sm text-[#667085]">
              {record.startTime || record.createTime || "-"} → {record.finishTime || "运行中"}
            </span>
          </div>

          <div className="mt-5 grid grid-cols-3 gap-4 max-md:grid-cols-1">
            {[
              ["读取", String(record.readRows ?? 0)],
              ["写入", String(record.writeRows ?? 0)],
              ["状态", meta.label],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg bg-[#fafafa] px-4 py-3">
                <div className="text-xs text-[#98a2b3]">{label}</div>
                <div className="mt-1 text-lg font-semibold text-[#344054]">{value}</div>
              </div>
            ))}
          </div>
        </section>

        {snapshot ? (
          <section className="rounded-lg border border-[#e6e8eb] bg-white">
            <h2 className="border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5 text-sm font-semibold text-[#344054]">
              任务配置快照
            </h2>
            <div className="p-5">
              <div className="flex items-center gap-4">
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-[#344054]">
                    {snapshot.source.dataSourceName || snapshot.source.dataSourceId}
                  </div>
                  <div className="mt-1 text-xs text-[#667085]">
                    {pathText(
                      snapshot.source.database,
                      snapshot.source.schema,
                      snapshot.source.table,
                    )}
                  </div>
                </div>
                <ArrowRight size={18} className="shrink-0 text-[#98a2b3]" />
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-[#344054]">
                    {snapshot.target.dataSourceName || snapshot.target.dataSourceId}
                  </div>
                  <div className="mt-1 text-xs text-[#667085]">
                    {pathText(
                      snapshot.target.database,
                      snapshot.target.schema,
                      snapshot.target.table,
                    )}
                  </div>
                </div>
              </div>

              <div className="mt-5 grid grid-cols-4 gap-3 text-xs max-lg:grid-cols-2">
                <div>Fetch Size：{snapshot.runtimeConfig.fetchSize}</div>
                <div>读取批次：{snapshot.runtimeConfig.readBatchSize}</div>
                <div>写入批次：{snapshot.runtimeConfig.writeBatchSize}</div>
                <div>超时：{snapshot.runtimeConfig.timeoutSeconds}s</div>
              </div>
            </div>
          </section>
        ) : null}

        {record.errorMessage ? (
          <section className="rounded-lg border border-[#fecdca] bg-[#fffbfa] p-4">
            <div className="text-sm font-medium text-[#b42318]">失败原因</div>
            <div className="mt-2 whitespace-pre-wrap break-words text-sm text-[#667085]">
              {record.errorMessage}
            </div>
          </section>
        ) : null}
      </div>
    </div>
  );
}

export default OfflineSyncInstanceDetailPage;
