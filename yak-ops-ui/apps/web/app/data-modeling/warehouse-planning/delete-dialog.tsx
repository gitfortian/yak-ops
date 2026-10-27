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
import { useQuery } from "@tanstack/react-query";
import { countLabel, searchTables } from "@/service/data-map/catalog";
import {
  deleteNode,
  planningFailureField,
  planningFailureMessage,
} from "@/service/data-modeling/warehouse-planning";
import type { PlanningNode } from "@/service/data-modeling/warehouse-planning/types";

interface Props {
  node: PlanningNode;
  onClose: () => void;
  onDeleted: () => void;
}

export default function DeleteDialog({ node, onClose, onDeleted }: Props) {
  const [deleting, setDeleting] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const busy = useRef(false);
  const active = useRef(true);
  /**
   * The count informs, it never blocks: a data map outage must not prevent a planning delete, so
   * the sentence is dropped instead of surfacing an error.
   */
  const domainAssets = useQuery({
    queryKey: ["data-map", "domain-asset-count", node.id],
    queryFn: ({ signal }) => searchTables({ domainId: node.id, pageNo: 1, pageSize: 1 }, signal),
    enabled: node.kind === "DOMAIN",
    retry: false,
  });
  const assetCount = node.kind === "DOMAIN" ? domainAssets.data?.pagination?.total : undefined;
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
      await deleteNode(node);
      if (active.current) {
        toast.success("删除成功");
        onDeleted();
      }
    } catch (error) {
      if (!active.current) return;
      if (planningFailureField(error) === "revision") setConflict(true);
      else setFailure(planningFailureMessage(error));
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
      <DialogContent className="rounded-xl shadow-none">
        <DialogTitle className="text-sm font-medium">删除{node.name}</DialogTitle>
        <DialogDescription className="my-4 text-sm">删除后无法恢复。</DialogDescription>
        {assetCount !== undefined && (
          <DialogDescription className="mb-4 text-sm">
            当前该数据域下约有 {countLabel(assetCount)} 张资产，删除后它们将变为未分类。
          </DialogDescription>
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
