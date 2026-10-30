import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useRef, useState } from "react";
import { classifyModelFailure, deleteModel } from "@/service/data-modeling/dimension-modeling";
import type { LogicalModel } from "@/service/data-modeling/dimension-modeling/types";

interface Props {
  model: LogicalModel;
  onClose: () => void;
  onDeleted: () => void;
}

export default function ModelDeleteDialog({ model, onClose, onDeleted }: Props) {
  const [deleting, setDeleting] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const busy = useRef(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const confirm = async () => {
    if (busy.current || conflict) return;
    busy.current = true;
    setDeleting(true);
    setFailure("");
    try {
      await deleteModel(model);
      if (active.current) {
        toast.success("删除成功");
        onDeleted();
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyModelFailure(error);
      if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "unknown" || result.kind === "field") setFailure(result.message);
      else setFailure("删除失败，请刷新后重试");
    } finally {
      busy.current = false;
      if (active.current) setDeleting(false);
    }
  };
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !busy.current) onClose();
      }}
    >
      <DialogContent className="w-[480px] rounded-xl p-5 shadow-none">
        <DialogTitle className="text-sm font-medium">删除逻辑模型</DialogTitle>
        <DialogDescription className="mt-3 text-sm text-[var(--yak-color-text-secondary)]">
          确认删除「{model.cnName}（{model.name}）」？该表的字段定义会一并删除，且无法恢复。
        </DialogDescription>
        <p className="mt-2 text-xs leading-5 text-[var(--yak-components-badge-warning-text)]">
          已发布的物理表不会被删除，也不会被回滚；如果这张表已经被发布过，请在目标库中手工处理，
          发布记录与元数据快照会保留。
        </p>
        {conflict && <Alert>数据已变更，请关闭弹窗并刷新后重试。</Alert>}
        {failure && <Alert>{failure}</Alert>}
        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={deleting} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="danger"
            loading={deleting}
            disabled={conflict}
            onClick={() => void confirm()}
          >
            删除
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
