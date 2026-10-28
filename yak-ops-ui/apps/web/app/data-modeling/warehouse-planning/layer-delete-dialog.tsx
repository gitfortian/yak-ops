import { Alert, Button, Dialog, DialogContent, DialogTitle, toast } from "@yak-ops/yak-ui";
import { useEffect, useRef, useState } from "react";
import {
  deleteLayer,
  layerFailureField,
  layerFailureMessage,
  type Layer,
} from "@/service/data-modeling/warehouse-planning/layers";

interface Props {
  layer: Layer;
  onClose: () => void;
  onDeleted: () => void;
}

export default function LayerDeleteDialog({ layer, onClose, onDeleted }: Props) {
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
      await deleteLayer(layer);
      if (active.current) {
        toast.success("删除成功");
        onDeleted();
      }
    } catch (error) {
      if (!active.current) return;
      if (layerFailureField(error) === "revision") setConflict(true);
      else setFailure(layerFailureMessage(error));
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
        <DialogTitle className="text-sm font-medium">删除分层</DialogTitle>
        <p className="mt-3 text-sm text-[var(--yak-color-text-secondary)]">
          确认删除分层「{layer.cnName}（{layer.enCode}）」？删除后该分层不再可选。
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
