import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogTitle,
  toast,
} from "@yak-ops/yak-ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";

import { dataMapFailureMessage } from "@/service/data-map/request";
import { replaceTableTags, tagFailureMessage } from "@/service/tags";

import { dataMapKeys } from "../../shared/keys";
import { useDataMapScope } from "../../shared/scope";
import { useTagDictionary } from "../../shared/lookups";

interface Props {
  tableId: string;
  selectedValueIds: string[];
  onClose: () => void;
  onSaved: () => void;
}

/**
 * Selector over the existing dictionary values for one asset.
 *
 * <p>The dictionary itself is managed in the tag console: this dialog only binds and unbinds, which
 * every Workspace member may do, and links there for the manager-owned create, rename and delete.
 */
export default function TagDialog({ tableId, selectedValueIds, onClose, onSaved }: Props) {
  const { scope } = useDataMapScope();
  const queryClient = useQueryClient();
  const dictionary = useTagDictionary();
  const [selected, setSelected] = useState<string[]>(selectedValueIds);
  const [failure, setFailure] = useState("");

  const save = useMutation({
    mutationFn: () => replaceTableTags(tableId, selected),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: dataMapKeys.tagDictionary(scope) });
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "tables"] });
      void queryClient.invalidateQueries({ queryKey: dataMapKeys.table(scope, tableId) });
      toast.success("标签已保存");
      onSaved();
    },
    onError: (error) => setFailure(tagFailureMessage(error, "保存失败，请稍后重试。")),
  });

  const toggle = (id: string, checked: boolean) =>
    setSelected((current) =>
      checked ? [...new Set([...current, id])] : current.filter((valueId) => valueId !== id),
    );

  const keys = dictionary.data ?? [];

  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !save.isPending) onClose();
      }}
    >
      <DialogContent className="w-[480px]">
        <DialogTitle className="text-[15px] font-semibold">编辑标签</DialogTitle>

        <div className="mt-4 max-h-72 space-y-3 overflow-auto">
          {keys.length === 0 && !dictionary.isPending && (
            <p className="text-xs leading-5 text-[var(--yak-components-muted-text)]">
              还没有标签键，先到
              <Link
                to="/data-governance/tags"
                className="mx-1 [color:var(--yak-color-primary)] hover:underline"
              >
                标签管理
              </Link>
              创建。
            </p>
          )}
          {keys.map((key) => (
            <section key={key.id}>
              <h3 className="text-xs font-medium text-[var(--yak-components-muted-text)]">
                {key.name}
              </h3>
              <div className="mt-1 space-y-1">
                {key.values.map((value) => (
                  <label
                    key={value.id}
                    className="flex cursor-pointer items-center gap-2 text-sm text-[var(--yak-components-panel-text)]"
                  >
                    <Checkbox
                      checked={selected.includes(value.id)}
                      aria-label={value.name}
                      onCheckedChange={(checked) => toggle(value.id, checked === true)}
                    />
                    <span className="min-w-0 flex-1 truncate">{value.name}</span>
                  </label>
                ))}
              </div>
            </section>
          ))}
        </div>

        {failure && <Alert className="mt-3">{failure}</Alert>}
        {dictionary.isError && (
          <Alert className="mt-3">
            {dataMapFailureMessage(dictionary.error, "标签加载失败，请稍后重试。")}
          </Alert>
        )}

        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={save.isPending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            loading={save.isPending}
            disabled={dictionary.isPending}
            onClick={() => save.mutate()}
          >
            保存
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
