import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
} from "@yak-ops/yak-ui";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { dataMapKeys } from "@/app/data-map/shared/keys";
import { useDataMapScope } from "@/app/data-map/shared/scope";
import { searchTables } from "@/service/data-map/catalog";
import { dataMapFailureMessage } from "@/service/data-map/request";
import {
  applyBindingBatch,
  tagFailureMessage,
  type TagBindingOperation,
  type TagKey,
} from "@/service/tags";

interface Props {
  keys: TagKey[];
  onClose: () => void;
  onApplied: (affected: number) => void;
}

const MAX_TABLES = 100;
const MAX_VALUES = 20;
const SEARCH_DEBOUNCE_MS = 300;

/**
 * Batch binding dialog: pick dictionary values, pick assets from the catalog, apply ADD or REMOVE.
 *
 * <p>The whole command is one server transaction, so the dialog collects the selection first and
 * lets the server reject an over-large batch with {@code BATCH_TOO_LARGE} rather than looping over
 * the single-asset endpoint.
 */
export default function BatchBindingDialog({ keys, onClose, onApplied }: Props) {
  const { scope, ready } = useDataMapScope();
  const [operation, setOperation] = useState<TagBindingOperation>("ADD");
  const [selectedValueIds, setSelectedValueIds] = useState<string[]>([]);
  const [selectedTableIds, setSelectedTableIds] = useState<string[]>([]);
  const [keyword, setKeyword] = useState("");
  const [settledKeyword, setSettledKeyword] = useState("");
  const [failure, setFailure] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => setSettledKeyword(keyword.trim()), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const assets = useQuery({
    queryKey: dataMapKeys.tables(scope, {
      keyword: settledKeyword || undefined,
      pageNo: 1,
      pageSize: MAX_TABLES,
    }),
    queryFn: ({ signal }) =>
      searchTables(
        { keyword: settledKeyword || undefined, pageNo: 1, pageSize: MAX_TABLES },
        signal,
      ),
    enabled: ready,
    placeholderData: (previous) => previous,
  });

  const apply = useMutation({
    mutationFn: () => applyBindingBatch(selectedTableIds, selectedValueIds, operation),
    onSuccess: (affected) => {
      onApplied(affected);
    },
    onError: (error) => setFailure(tagFailureMessage(error, "批量绑定失败，请稍后重试。")),
  });

  const toggleValue = (valueId: string, checked: boolean) =>
    setSelectedValueIds((current) =>
      checked ? [...new Set([...current, valueId])] : current.filter((id) => id !== valueId),
    );

  const toggleTable = (tableId: string, checked: boolean) =>
    setSelectedTableIds((current) =>
      checked ? [...new Set([...current, tableId])] : current.filter((id) => id !== tableId),
    );

  const records = assets.data?.bizData ?? [];
  const canSubmit = selectedValueIds.length > 0 && selectedTableIds.length > 0 && !apply.isPending;

  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => !open && !apply.isPending && onClose()}
    >
      <DialogContent className="max-h-[85vh] w-[720px] overflow-hidden">
        <DialogTitle className="text-sm font-medium">批量绑定标签</DialogTitle>
        <DialogDescription className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
          一次最多 100 张资产、20 个标签值、1000 条绑定；整个批量操作是一个事务。
        </DialogDescription>

        <div className="mt-4 grid min-h-0 grid-cols-1 gap-4 md:grid-cols-2">
          <section className="flex min-h-0 flex-col">
            <h3 className="text-xs font-medium text-[var(--yak-components-muted-text)]">
              选择标签值（{selectedValueIds.length}/{MAX_VALUES}）
            </h3>
            <div className="mt-2 min-h-0 flex-1 overflow-auto rounded-lg border border-[var(--yak-components-control-border)] p-2">
              {keys.length === 0 && (
                <p className="text-xs text-[var(--yak-components-muted-text)]">还没有标签值。</p>
              )}
              {keys.map((key) => (
                <div key={key.id} className="mb-2">
                  <p className="text-xs text-[var(--yak-components-muted-text)]">{key.name}</p>
                  {key.values.map((value) => (
                    <label
                      key={value.id}
                      className="flex cursor-pointer items-center gap-2 py-0.5 text-sm text-[var(--yak-components-panel-text)]"
                    >
                      <Checkbox
                        checked={selectedValueIds.includes(value.id)}
                        aria-label={value.name}
                        onCheckedChange={(checked) => toggleValue(value.id, checked === true)}
                      />
                      <span className="min-w-0 flex-1 truncate">{value.name}</span>
                    </label>
                  ))}
                </div>
              ))}
            </div>
          </section>

          <section className="flex min-h-0 flex-col">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-xs font-medium text-[var(--yak-components-muted-text)]">
                选择资产（{selectedTableIds.length}/{MAX_TABLES}）
              </h3>
              <Input
                size="small"
                variant="outlined"
                value={keyword}
                placeholder="搜索表名"
                aria-label="搜索资产"
                className="w-40"
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>
            <div className="mt-2 min-h-0 flex-1 overflow-auto rounded-lg border border-[var(--yak-components-control-border)] p-2">
              {assets.isError && (
                <Alert>{dataMapFailureMessage(assets.error, "资产加载失败，请稍后重试。")}</Alert>
              )}
              {records.length === 0 && !assets.isPending && (
                <p className="text-xs text-[var(--yak-components-muted-text)]">没有匹配的资产。</p>
              )}
              {records.map((record) => (
                <label
                  key={record.id}
                  className="flex cursor-pointer items-center gap-2 py-0.5 text-sm text-[var(--yak-components-panel-text)]"
                >
                  <Checkbox
                    checked={selectedTableIds.includes(record.id)}
                    aria-label={record.name}
                    onCheckedChange={(checked) => toggleTable(record.id, checked === true)}
                  />
                  <span className="min-w-0 flex-1 truncate" title={record.name}>
                    {record.name}
                  </span>
                </label>
              ))}
            </div>
          </section>
        </div>

        <div className="mt-4 flex items-center gap-2">
          <span className="text-xs text-[var(--yak-components-muted-text)]">操作</span>
          <Button
            size="small"
            variant={operation === "ADD" ? "primary" : "ghost"}
            onClick={() => setOperation("ADD")}
          >
            添加
          </Button>
          <Button
            size="small"
            variant={operation === "REMOVE" ? "primary" : "ghost"}
            onClick={() => setOperation("REMOVE")}
          >
            移除
          </Button>
        </div>

        {failure && <Alert className="mt-3">{failure}</Alert>}

        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={apply.isPending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            loading={apply.isPending}
            disabled={!canSubmit}
            onClick={() => {
              setFailure("");
              apply.mutate();
            }}
          >
            确认批量绑定
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
