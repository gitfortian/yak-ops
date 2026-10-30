import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@yak-ops/yak-ui";

import type { CrawlTask } from "@/service/data-map/types";

interface BatchProps {
  kind: "offline" | "delete" | null;
  count: number;
  pending: boolean;
  /** Deleting a task that is still armed is rejected by the backend; say so before submitting. */
  blocked: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export function BatchConfirmDialog({
  kind,
  count,
  pending,
  blocked,
  onClose,
  onConfirm,
}: BatchProps) {
  return (
    <Dialog
      open={kind !== null}
      onOpenChange={(open) => {
        if (!open && !pending) onClose();
      }}
    >
      <DialogContent className="w-[430px]">
        <DialogTitle className="text-sm font-medium">
          {kind === "delete" ? `删除 ${count} 个采集任务？` : `下线 ${count} 个采集任务？`}
        </DialogTitle>
        <DialogDescription className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
          {kind === "delete"
            ? "只移除任务配置与运行记录，已采集的资产保留在资产目录里。"
            : "下线后停止调度，配置与历史保留，可随时重新上线。"}
        </DialogDescription>
        {kind === "delete" && blocked && (
          <Alert className="mt-3">所选任务中包含已上线的任务，请先下线后再删除。</Alert>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={pending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant={kind === "delete" ? "danger" : "primary"}
            loading={pending}
            disabled={count === 0}
            onClick={onConfirm}
          >
            确认
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

interface DeleteProps {
  task: CrawlTask | null;
  pending: boolean;
  onClose: () => void;
  onConfirm: (taskId: string) => void;
}

export function DeleteTaskDialog({ task, pending, onClose, onConfirm }: DeleteProps) {
  return (
    <Dialog
      open={task !== null}
      onOpenChange={(open) => {
        if (!open && !pending) onClose();
      }}
    >
      <DialogContent className="w-[430px]">
        <DialogTitle className="text-sm font-medium">删除采集任务「{task?.name}」？</DialogTitle>
        <DialogDescription className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
          只移除任务配置与运行记录，已采集的资产保留在资产目录里。
        </DialogDescription>
        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={pending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="danger"
            loading={pending}
            onClick={() => task && onConfirm(task.id)}
          >
            删除
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
