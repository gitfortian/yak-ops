import { Alert, Badge, Table, type BadgeProps } from "@yak-ops/yak-ui";
import { ArrowRight } from "lucide-react";

import type {
  DataSyncAttemptRecord,
  DataSyncInstanceRecord,
  DataSyncInstanceStatus,
} from "@/service/data-sync";

export const isActiveDataSyncInstance = (record?: DataSyncInstanceRecord) =>
  record?.status === "PENDING" ||
  record?.status === "RUNNING" ||
  record?.status === "RETRY_WAITING";

export const dataSyncInstanceStatusMeta = (
  status: DataSyncInstanceStatus,
  realtime: boolean,
): { label: string; tone: BadgeProps["tone"] } => {
  switch (status) {
    case "PENDING":
      return { label: "等待", tone: "neutral" };
    case "RUNNING":
      return { label: "运行中", tone: "info" };
    case "RETRY_WAITING":
      return { label: "等待重试", tone: "warning" };
    case "SUCCEEDED":
      return { label: "成功", tone: "success" };
    case "FAILED":
      return { label: "失败", tone: "danger" };
    case "CANCELED":
      return { label: realtime ? "已停止" : "已取消", tone: "neutral" };
    case "LOST":
      return { label: "已丢失", tone: "warning" };
    default:
      return { label: status || "-", tone: "neutral" };
  }
};

export const dataSyncTriggerText = (triggerType?: string) => {
  if (triggerType === "MANUAL") return "手动运行";
  if (triggerType === "SCHEDULE") return "调度触发";
  if (triggerType === "AUTO_RECOVERY") return "自动恢复";
  if (triggerType === "RETRY") return "重试";
  return triggerType || "-";
};

export const dataSyncDurationText = (record: DataSyncInstanceRecord) => {
  if (!record.startTime) return "-";
  const start = Date.parse(record.startTime);
  const end = record.finishTime ? Date.parse(record.finishTime) : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end)) return "-";
  const seconds = Math.max(0, Math.floor((end - start) / 1000));
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s`;
};

const pathText = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

interface DataSyncExecutionDetailContentProps {
  record: DataSyncInstanceRecord;
  attempts: DataSyncAttemptRecord[];
  realtime: boolean;
  showRealtimeStopCaution?: boolean;
}

export function DataSyncExecutionDetailContent({
  record,
  attempts,
  realtime,
  showRealtimeStopCaution = false,
}: DataSyncExecutionDetailContentProps) {
  const meta = dataSyncInstanceStatusMeta(record.status, realtime);
  const snapshot = record.definitionSnapshot;
  const readLabel = realtime ? "读取事件" : "读取";
  const writeLabel = realtime ? "写入事件" : "写入";

  return (
    <div className="space-y-4">
      {showRealtimeStopCaution && realtime && isActiveDataSyncInstance(record) ? (
        <Alert>停止后将从最近 Checkpoint 续跑，少量未确认事件可能重复消费。</Alert>
      ) : null}

      <section className="rounded-lg border border-[#e6e8eb] bg-white p-5">
        <div className="flex flex-wrap items-center gap-3">
          <Badge tone={meta.tone}>{meta.label}</Badge>
          <span className="text-sm text-[#667085]">
            {record.startTime || record.createTime || "-"} → {record.finishTime || "运行中"}
          </span>
          <span className="text-xs text-[#98a2b3]">{dataSyncTriggerText(record.triggerType)}</span>
        </div>

        <div className="mt-5 grid grid-cols-4 gap-4 max-lg:grid-cols-2 max-md:grid-cols-1">
          {[
            [readLabel, (record.readRows ?? 0).toLocaleString()],
            [writeLabel, (record.writeRows ?? 0).toLocaleString()],
            ["Attempt", `${record.currentAttempt || 1} / ${record.maxAttempts || 1}`],
            ["耗时", dataSyncDurationText(record)],
          ].map(([label, value]) => (
            <div key={label} className="rounded-lg bg-[#fafafa] px-4 py-3">
              <div className="text-xs text-[#98a2b3]">{label}</div>
              <div className="mt-1 text-lg font-semibold text-[#344054]">{value}</div>
            </div>
          ))}
        </div>

        {realtime ? (
          <div className="mt-3 text-xs leading-5 text-[#98a2b3]">
            实时指标统计 YakFlow 变更事件；UPDATE 会产生 UPDATE_BEFORE 与 UPDATE_AFTER 两个事件。
          </div>
        ) : null}
      </section>

      <section className="overflow-hidden rounded-lg border border-[#e6e8eb] bg-white">
        <h2 className="border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5 text-sm font-semibold text-[#344054]">
          Attempt 历史
        </h2>
        <Table<DataSyncAttemptRecord>
          columns={[
            {
              key: "attemptNo",
              title: "Attempt",
              width: 90,
              render: (_value, attempt) => `#${attempt.attemptNo}`,
            },
            {
              key: "status",
              title: "状态",
              width: 110,
              render: (_value, attempt) => {
                const attemptMeta = dataSyncInstanceStatusMeta(attempt.status, realtime);
                return <Badge tone={attemptMeta.tone}>{attemptMeta.label}</Badge>;
              },
            },
            {
              key: "time",
              title: "开始 / 完成",
              minWidth: 260,
              render: (_value, attempt) => (
                <div className="text-xs text-[#667085]">
                  {attempt.startTime || attempt.createTime || "-"} →{" "}
                  {attempt.finishTime || "运行中"}
                </div>
              ),
            },
            {
              key: "metrics",
              title: "读取 / 写入",
              width: 140,
              align: "right",
              render: (_value, attempt) =>
                `${(attempt.readRows ?? 0).toLocaleString()} / ${(attempt.writeRows ?? 0).toLocaleString()}`,
            },
            {
              key: "error",
              title: "错误",
              minWidth: 220,
              render: (_value, attempt) => (
                <div
                  className="max-w-[360px] truncate text-xs text-[#667085]"
                  title={attempt.errorMessage || ""}
                >
                  {attempt.errorMessage || "-"}
                </div>
              ),
            },
          ]}
          dataSource={attempts}
          rowKey="id"
          bordered
          size="small"
          pagination={false}
          emptyText="旧实例或未执行实例暂无独立 Attempt 记录"
          scroll={{ x: 900 }}
        />
        {record.status === "RETRY_WAITING" ? (
          <div className="border-t border-[#eef0f3] px-4 py-3 text-xs text-[#b54708]">
            下一次重试：{record.nextRetryTime || "待执行"} · Backoff {record.backoffSeconds || 0}s
          </div>
        ) : null}
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

            {snapshot.runtimeConfig ? (
              <div className="mt-5 grid grid-cols-4 gap-3 text-xs max-lg:grid-cols-2">
                <div>Fetch Size：{snapshot.runtimeConfig.fetchSize}</div>
                <div>读取批次：{snapshot.runtimeConfig.readBatchSize}</div>
                <div>写入批次：{snapshot.runtimeConfig.writeBatchSize}</div>
                <div>超时：{snapshot.runtimeConfig.timeoutSeconds}s</div>
              </div>
            ) : null}

            {snapshot.realtimeConfig ? (
              <div className="mt-5 grid grid-cols-5 gap-3 text-xs max-xl:grid-cols-3 max-lg:grid-cols-2">
                <div>Checkpoint：{snapshot.realtimeConfig.checkpointIntervalSeconds}s</div>
                <div>CDC 队列：{snapshot.realtimeConfig.queueCapacity}</div>
                <div>读取批次：{snapshot.realtimeConfig.pollBatchSize}</div>
                <div>写入批次：{snapshot.realtimeConfig.writeBatchSize}</div>
                <div>超时：{snapshot.realtimeConfig.timeoutSeconds}s</div>
              </div>
            ) : null}
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
  );
}
