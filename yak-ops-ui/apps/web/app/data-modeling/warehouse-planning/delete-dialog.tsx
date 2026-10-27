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
import { useDataMapScope } from "@/app/data-map/shared/scope";
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
  const { scope, ready } = useDataMapScope();
  /**
   * The count informs, it never blocks: a data map outage must not prevent a planning delete, so
   * the sentence degrades to an unavailable notice instead of surfacing an error.
   *
   * <p>The key carries the Workspace identity, otherwise switching Workspace would render the
   * previous Workspace's asset count as if it described this domain.
   */
  const domainAssets = useQuery({
    queryKey: ["data-map", "domain-asset-count", ...scope, node.id],
    queryFn: ({ signal }) => searchTables({ domainId: node.id, pageNo: 1, pageSize: 1 }, signal),
    enabled: ready && node.kind === "DOMAIN",
    retry: false,
    staleTime: 0,
  });
  const isDomain = node.kind === "DOMAIN";
  const assetCount = domainAssets.data?.pagination?.total;
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
        {isDomain && (
          <DialogDescription className="mb-4 text-sm">
            {assetCount === undefined
              ? domainAssets.isError
                ? "关联资产数量暂时无法获取，删除后该数据域下的资产将变为未分类。"
                : "正在统计关联资产…"
              : assetCount === 0
                ? "当前该数据域下没有关联资产。"
                : `当前该数据域下约有 ${countLabel(assetCount)} 张资产，删除后它们将变为未分类。`}
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
