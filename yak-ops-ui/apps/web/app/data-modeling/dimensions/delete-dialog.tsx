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
import {
  classifyDimensionFailure,
  deleteDimension,
} from "@/service/data-modeling/dimension-modeling";
import type { ConceptDimension } from "@/service/data-modeling/dimension-modeling/types";

interface Props {
  dimension: ConceptDimension;
  /**
   * Logical tables the loaded catalog still binds to the dimension. It blocks the dialog before the
   * request leaves; the server keeps rejecting the delete even when this count is incomplete.
   */
  boundModelCount: number;
  onClose: () => void;
  onDeleted: () => void;
}

export default function DimensionDeleteDialog({
  dimension,
  boundModelCount,
  onClose,
  onDeleted,
}: Props) {
  const [deleting, setDeleting] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [referenced, setReferenced] = useState(false);
  const [failure, setFailure] = useState("");
  const blocked = boundModelCount > 0 || referenced;
  const busy = useRef(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const confirm = async () => {
    if (busy.current || conflict || blocked) return;
    busy.current = true;
    setDeleting(true);
    setFailure("");
    try {
      await deleteDimension(dimension);
      if (active.current) {
        toast.success("删除成功");
        onDeleted();
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyDimensionFailure(error);
      if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "referenced") setReferenced(true);
      else if (result.kind === "unknown") setFailure(result.message);
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
        <DialogTitle className="text-sm font-medium">删除概念维度</DialogTitle>
        <DialogDescription className="mt-3 text-sm text-[var(--yak-color-text-secondary)]">
          {blocked
            ? `「${dimension.name}（${dimension.code}）」暂时无法删除。`
            : `确认删除「${dimension.name}（${dimension.code}）」？`}
        </DialogDescription>
        {blocked ? (
          <Alert>
            {boundModelCount > 0
              ? `该维度下还有 ${boundModelCount} 张关联逻辑表，请先删除或解绑关联的维度表。`
              : "该维度仍被逻辑表引用，请先删除或解绑关联的维度表。"}
          </Alert>
        ) : (
          <p className="mt-1 text-xs text-[var(--yak-color-text-secondary)]">
            删除维度概念不会自动删除已发布的物理表。
          </p>
        )}
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
            disabled={conflict || blocked}
            onClick={() => void confirm()}
          >
            删除
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
