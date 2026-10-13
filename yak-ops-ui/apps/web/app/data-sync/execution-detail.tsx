import { Badge, Button, SectionCard, Table, type BadgeProps, type TableColumns } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

import { canCancelTaskInstance, isTaskInstanceActive } from "@/service/data-sync/execution";
import type { DataSyncInstanceRecord, DataSyncInstanceStatus } from "@/service/data-sync";

export const isActiveDataSyncInstance = (record?: DataSyncInstanceRecord): boolean =>
  record ? isTaskInstanceActive(record.status) : false;

export const dataSyncInstanceStatusMeta = (
  status: DataSyncInstanceStatus,
  realtime: boolean,
): { label: string; tone: BadgeProps["tone"] } => {
  switch (status) {
    case "SUBMITTED":
      return { label: "等待", tone: "neutral" };
    case "LAUNCHING":
      return { label: "启动中", tone: "info" };
    case "RUNNING":
      return { label: "运行中", tone: "info" };
    case "CANCELLING":
      return { label: "正在停止", tone: "warning" };
    case "RECONCILING":
      return { label: "待确认", tone: "warning" };
    case "FINISHED":
      return { label: "成功", tone: "success" };
    case "FAILED":
      return { label: "失败", tone: "danger" };
    case "CANCELED":
      return { label: realtime ? "已停止" : "已取消", tone: "neutral" };
  }
};

export const dataSyncTriggerText = (triggerType?: string) => {
  if (triggerType === "MANUAL") return "手动";
  if (triggerType === "SCHEDULE") return "调度";
  if (triggerType === "WORKFLOW") return "工作流";
  return triggerType || "-";
};

export const dataSyncDurationText = (record: DataSyncInstanceRecord) => {
  if (!record.startTime) return "-";
  const start = Date.parse(record.startTime);
  const end = record.finishTime ? Date.parse(record.finishTime) : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end)) return "-";
  const seconds = Math.max(0, Math.floor((end - start) / 1000));
  if (seconds < 60) return seconds + "s";
  const minutes = Math.floor(seconds / 60);
  return minutes + "m " + (seconds % 60) + "s";
};

function Value({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <div className="text-xs text-[#98a2b3]">{label}</div>
      <div className="mt-1 break-words text-sm text-[#344054]">{children}</div>
    </div>
  );
}

const tablePath = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

export const dataSyncWriteModeText = (value?: string) => {
  if (value === "APPEND") return "追加写入";
  if (value === "OVERWRITE") return "覆盖写入";
  if (value === "UPSERT") return "更新插入";
  return value || "-";
};

const rowCountText = (value?: number) =>
  value === undefined ? "-" : value.toLocaleString("zh-CN");

interface ConfigProps {
  record: DataSyncInstanceRecord;
  realtime: boolean;
  sectionCard?: boolean;
}

export function DataSyncExecutionConfigContent({ record, sectionCard = true }: ConfigProps) {
  const snapshot = record.definitionSnapshot;
  if (!snapshot) return null;

  const content = (
    <div className="grid grid-cols-2 gap-5 max-md:grid-cols-1">
      <div className="space-y-4">
        <Value label="来源数据源">{snapshot.source.dataSourceName || "-"}</Value>
        <Value label="来源表">
          {tablePath(snapshot.source.database, snapshot.source.schema, snapshot.source.table)}
        </Value>
        <Value label="实例 ID">{record.id}</Value>
      </div>
      <div className="space-y-4">
        <Value label="去向数据源">{snapshot.target.dataSourceName || "-"}</Value>
        <Value label="去向表">
          {tablePath(snapshot.target.database, snapshot.target.schema, snapshot.target.table)}
        </Value>
        <Value label="写入方式">{dataSyncWriteModeText(snapshot.writeMode)}</Value>
        {record.runtimeId ? <Value label="Job ID">{record.runtimeId}</Value> : null}
      </div>
    </div>
  );

  if (!sectionCard) return content;
  return <SectionCard title="配置快照">{content}</SectionCard>;
}

interface DetailProps {
  record: DataSyncInstanceRecord;
  realtime: boolean;
  onViewLog?: () => void;
}

interface TableExecutionRow {
  id: string;
  sourceTable: string;
  targetTable: string;
  status: DataSyncInstanceStatus;
  readRows?: number;
  writeRows?: number;
  duration: string;
}

export function DataSyncExecutionDetailContent({ record, realtime, onViewLog }: DetailProps) {
  const meta = dataSyncInstanceStatusMeta(record.status, realtime);
  const snapshot = record.definitionSnapshot;
  const rows: TableExecutionRow[] = snapshot
    ? [
        {
          id: record.id,
          sourceTable: tablePath(
            snapshot.source.database,
            snapshot.source.schema,
            snapshot.source.table,
          ),
          targetTable: tablePath(
            snapshot.target.database,
            snapshot.target.schema,
            snapshot.target.table,
          ),
          status: record.status,
          readRows: record.readRows,
          writeRows: record.writeRows,
          duration: dataSyncDurationText(record),
        },
      ]
    : [];
  const columns: TableColumns<TableExecutionRow> = [
    {
      key: "sourceTable",
      title: "来源表",
      minWidth: 180,
      render: (_value, row) => <span className="break-all">{row.sourceTable}</span>,
    },
    {
      key: "targetTable",
      title: "去向表",
      minWidth: 180,
      render: (_value, row) => <span className="break-all">{row.targetTable}</span>,
    },
    {
      key: "status",
      title: "执行状态",
      width: 110,
      render: (_value, row) => {
        const status = dataSyncInstanceStatusMeta(row.status, realtime);
        return <Badge tone={status.tone}>{status.label}</Badge>;
      },
    },
    {
      key: "readRows",
      title: "读取条数",
      width: 110,
      align: "right",
      render: (_value, row) => rowCountText(row.readRows),
    },
    {
      key: "writeRows",
      title: "写入条数",
      width: 110,
      align: "right",
      render: (_value, row) => rowCountText(row.writeRows),
    },
    {
      key: "duration",
      title: "耗时",
      width: 90,
      render: (_value, row) => row.duration,
    },
  ];

  return (
    <div className="space-y-4">
      <SectionCard title="执行状态">
        <div className="flex flex-wrap items-center gap-3">
          <Badge tone={meta.tone}>{meta.label}</Badge>
          <span className="text-sm text-[#667085]">
            v{record.taskVersion} · {dataSyncTriggerText(record.triggerType)}
          </span>
        </div>
        <div className="mt-4 grid grid-cols-3 gap-5 max-md:grid-cols-1">
          <Value label="开始时间">{record.startTime || record.createTime || "-"}</Value>
          <Value label="完成时间">{record.finishTime || "-"}</Value>
          <Value label="耗时">{dataSyncDurationText(record)}</Value>
        </div>
        {record.status === "FAILED" ? (
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <span className="text-sm text-[#b42318]">
              {record.errorMessage ? "失败代码：" + record.errorMessage : "执行失败"}
            </span>
            {onViewLog ? (
              <Button size="small" variant="ghost" onClick={onViewLog}>
                查看运行日志
              </Button>
            ) : null}
          </div>
        ) : null}
        {record.status === "RECONCILING" ? (
          <div className="mt-4 text-sm text-[#b54708]">执行所有权尚未确认，目标资源仍受保护</div>
        ) : null}
        {canCancelTaskInstance(record.status) && realtime ? (
          <div className="mt-4 text-xs text-[#667085]">停止不会自动恢复；重新运行会创建新实例</div>
        ) : null}
      </SectionCard>

      {record.readRows !== undefined || record.writeRows !== undefined ? (
        <SectionCard title="同步指标">
          <div className="grid grid-cols-2 gap-5 max-md:grid-cols-1">
            <Value label="读取条数">{rowCountText(record.readRows)}</Value>
            <Value label="写入条数">{rowCountText(record.writeRows)}</Value>
          </div>
        </SectionCard>
      ) : null}

      {snapshot ? (
        <SectionCard title="表级执行明细">
          <Table<TableExecutionRow>
            columns={columns}
            dataSource={rows}
            rowKey="id"
            bordered
            size="small"
            scroll={{ x: 820 }}
            pagination={false}
          />
        </SectionCard>
      ) : null}
    </div>
  );
}
