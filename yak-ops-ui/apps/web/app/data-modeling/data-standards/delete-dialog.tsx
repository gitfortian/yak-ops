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
  checkStandardReferenced,
  classifyStandardFailure,
  deleteStandardTarget,
} from "@/service/data-modeling/data-standards";
import type { DeleteTarget } from "@/service/data-modeling/data-standards/types";
export default function DeleteDialog(props: {
  target: DeleteTarget;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const [status, setStatus] = useState<"loading" | "ready" | "error" | "referenced">(
    props.target.kind === "field" ? "loading" : "ready",
  );
  const [version, setVersion] = useState(0);
  const [deleting, setDeleting] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const [blocked, setBlocked] = useState(false);
  const active = useRef(true),
    busy = useRef(false);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  useEffect(() => {
    if (props.target.kind !== "field") return;
    const controller = new AbortController();
    let current = true;
    setStatus("loading");
    void checkStandardReferenced(props.target.node.id, controller.signal)
      .then((result) => {
        if (current) {
          const referenced = result.availability === "AVAILABLE" && result.referenced;
          setStatus(referenced ? "referenced" : "ready");
        }
      })
      .catch(() => {
        if (current) setStatus("error");
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [props.target, version]);
  const confirm = async () => {
    if (busy.current || status !== "ready" || conflict || blocked) return;
    busy.current = true;
    setDeleting(true);
    setFailure("");
    try {
      await deleteStandardTarget(props.target);
      if (active.current) {
        toast.success("删除成功");
        props.onDeleted();
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyStandardFailure(error);
      if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing") {
        setBlocked(true);
        setFailure("该记录已不存在，请关闭弹窗并刷新列表。");
      } else if (result.kind === "forbidden") {
        setBlocked(true);
        setFailure("无权删除该记录。");
      } else setFailure(result.kind === "unknown" ? result.message : "删除失败，请稍后重试");
    } finally {
      busy.current = false;
      if (active.current) setDeleting(false);
    }
  };
  const name = props.target.kind === "category" ? props.target.node.name : props.target.node.cnName;
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !busy.current) props.onClose();
      }}
    >
      <DialogContent className="rounded-xl shadow-none">
        <DialogTitle className="text-sm font-medium">删除{name}</DialogTitle>
        <DialogDescription className="my-4 text-sm">删除后无法恢复。</DialogDescription>
        {status === "error" && (
          <Alert>
            引用状态读取失败
            <Button size="small" onClick={() => setVersion((value) => value + 1)}>
              重试
            </Button>
          </Alert>
        )}
        {status === "referenced" && <Alert>该标准已被资产引用，无法删除</Alert>}
        {conflict && <Alert>数据已变更，请关闭弹窗并刷新后重试。</Alert>}
        {failure && <Alert>{failure}</Alert>}
        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={deleting} onClick={props.onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="danger"
            loading={deleting || status === "loading"}
            disabled={status !== "ready" || conflict || blocked}
            onClick={() => void confirm()}
          >
            删除
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
