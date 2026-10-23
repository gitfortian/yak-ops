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
  onClose: () => void;
  onDeleted: () => void;
}

export default function DimensionDeleteDialog({ dimension, onClose, onDeleted }: Props) {
  const [deleting, setDeleting] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [blocked, setBlocked] = useState(false);
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
      else if (result.kind === "referenced") setBlocked(true);
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
        <DialogDescription className="mt-3 text-sm text-[#475467]">
          确认删除「{dimension.name}（{dimension.code}）」？
        </DialogDescription>
        {blocked && <Alert>该维度已被维度表引用，请先解除模型中的绑定后再删除。</Alert>}
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
