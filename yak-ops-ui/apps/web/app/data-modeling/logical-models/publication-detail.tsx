import { Badge, Button, Dialog, DialogContent, DialogTitle } from "@yak-ops/yak-ui";
import {
  PUBLICATION_OPERATION_LABEL,
  PUBLICATION_STATUS_LABEL,
  type PublicationRecord,
} from "@/service/data-modeling/dimension-modeling/publication";

const STATUS_TONE: Record<string, "info" | "success" | "danger" | "warning" | "neutral"> = {
  PENDING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};

const CHANGE_COLOR: Record<string, string> = {
  ADD: "text-[#067647]",
  CHANGE: "text-[#b54708]",
  DROP: "text-[#b42318]",
};

const formatTime = (value: string | null) => (value ? value.replace("T", " ").slice(0, 23) : "-");

interface Props {
  record: PublicationRecord | null;
  onClose: () => void;
}

/**
 * Read-only detail of one publication record: the diff that was computed, the DDL that ran, the
 * duration and the full execution log with stack traces. Everything shown here is what the record
 * persisted, so a failure can be diagnosed without asking the engine anything.
 */
export default function PublicationDetailDialog({ record, onClose }: Props) {
  if (!record) return null;
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="w-[720px] max-w-[95vw] rounded-xl p-5 shadow-none">
        <DialogTitle className="text-sm font-medium">发布详情</DialogTitle>

        <div className="mt-3 grid grid-cols-2 gap-3 text-xs">
          <div className="grid gap-1">
            <span className="font-medium">状态</span>
            <span>
              <Badge tone={STATUS_TONE[record.status ?? ""] ?? "neutral"}>
                {record.status ? PUBLICATION_STATUS_LABEL[record.status] : "未知状态"}
              </Badge>
            </span>
          </div>
          <div className="grid gap-1">
            <span className="font-medium">变更类型</span>
            <span className="text-[#475467]">
              {record.operation ? PUBLICATION_OPERATION_LABEL[record.operation] : "-"}
            </span>
          </div>
          <div className="grid gap-1">
            <span className="font-medium">目标地址</span>
            <span className="text-[#475467]">
              {[record.database, record.schema].filter(Boolean).join(".")}.{record.table}
            </span>
          </div>
          <div className="grid gap-1">
            <span className="font-medium">执行耗时</span>
            <span className="text-[#475467]">
              {record.durationMs === null ? "-" : `${record.durationMs} ms`}
            </span>
          </div>
          <div className="grid gap-1">
            <span className="font-medium">发起时间</span>
            <span className="text-[#475467]">{formatTime(record.createdTime)}</span>
          </div>
          <div className="grid gap-1">
            <span className="font-medium">结束时间</span>
            <span className="text-[#475467]">{formatTime(record.finishedTime)}</span>
          </div>
        </div>

        {record.error && (
          <div className="mt-3 rounded-lg border border-[#f04438] bg-[#fef3f2] px-3 py-2 text-xs text-[#b42318]">
            {record.error}
          </div>
        )}

        {record.changes.length > 0 && (
          <div className="mt-3">
            <h4 className="text-xs font-medium">变更对比</h4>
            <ul className="mt-1 grid gap-1 text-xs">
              {record.changes.map((change) => (
                <li
                  key={`${change.kind}-${change.column}`}
                  className={CHANGE_COLOR[change.kind] ?? ""}
                >
                  {change.detail}
                </li>
              ))}
            </ul>
          </div>
        )}

        {record.ddl && (
          <div className="mt-3">
            <h4 className="text-xs font-medium">执行的 DDL</h4>
            <pre className="mt-1 max-h-44 overflow-auto rounded-lg border border-[#e4e7ec] bg-[#f9fafb] p-3 text-xs leading-5 whitespace-pre-wrap text-[#344054]">
              {record.ddl}
            </pre>
          </div>
        )}

        <div className="mt-3">
          <h4 className="text-xs font-medium">执行日志</h4>
          {record.executionLog ? (
            <pre className="mt-1 max-h-56 overflow-auto rounded-lg border border-[#e4e7ec] bg-[#f9fafb] p-3 text-xs leading-5 whitespace-pre-wrap text-[#344054]">
              {record.executionLog}
            </pre>
          ) : (
            <p className="mt-1 text-xs text-[#98a2b3]">该记录没有执行日志。</p>
          )}
        </div>

        <div className="mt-4 flex justify-end">
          <Button size="small" onClick={onClose}>
            关闭
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
