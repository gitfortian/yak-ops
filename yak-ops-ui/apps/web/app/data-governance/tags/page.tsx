import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  PageHeader,
  toast,
} from "@yak-ops/yak-ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Tags } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import AccentBadge, { accentToneOf } from "@/app/data-map/shared/accent-badge";
import HeatBar from "@/app/data-map/shared/heat-bar";
import { dataMapKeys } from "@/app/data-map/shared/keys";
import { useDataMapScope } from "@/app/data-map/shared/scope";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  addValue,
  createKey,
  deleteKey,
  deleteValue,
  listKeys,
  renameKey,
  renameValue,
  tagFailureMessage,
} from "@/service/tags";

import BatchBindingDialog from "./batch-dialog";
import KeyDialog, { type KeyDraft } from "./key-dialog";
import ValueDialog, { type ValueDraft } from "./value-dialog";

/** Only a Workspace manager writes the dictionary; every member may bind and unbind. */
function useCanManageDictionary() {
  const { currentWorkspace } = useWorkspace();
  const role = currentWorkspace?.role;
  return role === "OWNER" || role === "ADMIN";
}

export default function TagConsolePage() {
  const navigate = useNavigate();
  const { scope, ready } = useDataMapScope();
  const queryClient = useQueryClient();
  const canManage = useCanManageDictionary();
  const [selectedKeyId, setSelectedKeyId] = useState("");
  const [keyDraft, setKeyDraft] = useState<KeyDraft | null>(null);
  const [valueDraft, setValueDraft] = useState<ValueDraft | null>(null);
  const [confirming, setConfirming] = useState<{ kind: "key" | "value"; id: string } | null>(null);
  const [batchOpen, setBatchOpen] = useState(false);

  const dictionary = useQuery({
    queryKey: dataMapKeys.tagDictionary(scope),
    queryFn: ({ signal }) => listKeys(signal),
    enabled: ready,
  });

  const keys = dictionary.data ?? [];
  const selectedKey = useMemo(
    () => keys.find((key) => key.id === selectedKeyId) ?? keys[0],
    [keys, selectedKeyId],
  );

  useEffect(() => {
    if (selectedKeyId || keys.length === 0) return;
    setSelectedKeyId(keys[0].id);
  }, [keys, selectedKeyId]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: dataMapKeys.tagDictionary(scope) });
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "tables"] });
  };

  const onDictionaryError = (error: unknown) => toast.error(tagFailureMessage(error));

  const create = useMutation({
    mutationFn: (input: { name: string; values: string[] }) => createKey(input.name, input.values),
    onSuccess: () => {
      setKeyDraft(null);
      refresh();
      toast.success("标签键已创建");
    },
    onError: onDictionaryError,
  });

  const rename = useMutation({
    mutationFn: (input: { keyId: string; name: string }) => renameKey(input.keyId, input.name),
    onSuccess: () => {
      setKeyDraft(null);
      refresh();
      toast.success("标签键已重命名");
    },
    onError: onDictionaryError,
  });

  const removeKey = useMutation({
    mutationFn: (keyId: string) => deleteKey(keyId),
    onSuccess: (_result, keyId) => {
      setConfirming(null);
      if (selectedKeyId === keyId) setSelectedKeyId("");
      refresh();
      toast.success("标签键已删除");
    },
    onError: (error) => {
      setConfirming(null);
      onDictionaryError(error);
    },
  });

  const add = useMutation({
    mutationFn: (input: { keyId: string; name: string }) => addValue(input.keyId, input.name),
    onSuccess: () => {
      setValueDraft(null);
      refresh();
      toast.success("标签值已创建");
    },
    onError: onDictionaryError,
  });

  const renameValueMutation = useMutation({
    mutationFn: (input: { valueId: string; name: string }) =>
      renameValue(input.valueId, input.name),
    onSuccess: () => {
      setValueDraft(null);
      refresh();
      toast.success("标签值已重命名");
    },
    onError: onDictionaryError,
  });

  const removeValue = useMutation({
    mutationFn: (valueId: string) => deleteValue(valueId),
    onSuccess: () => {
      setConfirming(null);
      refresh();
      toast.success("标签值已删除");
    },
    onError: (error) => {
      setConfirming(null);
      onDictionaryError(error);
    },
  });

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--yak-color-background)]">
      <PageHeader
        title="标签管理"
        description="维护工作空间的标签键与标签值；资产上的标签在数据地图中按 key = value 展示与筛选。"
        className="px-6 pb-5 pt-5 max-md:px-4"
        extra={
          <div className="flex items-center gap-2">
            <Button size="small" onClick={() => setBatchOpen(true)} disabled={keys.length === 0}>
              批量绑定
            </Button>
            {canManage && (
              <Button
                size="small"
                variant="primary"
                onClick={() => setKeyDraft({ mode: "create", name: "", values: "" })}
              >
                新建标签键
              </Button>
            )}
          </div>
        }
      />

      {dictionary.isError && (
        <Alert className="mx-6 mt-2">
          {tagFailureMessage(dictionary.error, "标签加载失败，请稍后重试。")}
        </Alert>
      )}

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 px-6 pb-6 max-md:px-4 md:grid-cols-[280px_minmax(0,1fr)]">
        <div className="flex min-h-0 flex-col overflow-hidden rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
          <div className="flex min-h-11 items-center justify-between gap-2 border-b border-[var(--yak-components-panel-border)] px-3">
            <h2 className="text-[13px] font-bold text-[var(--yak-color-text-primary)]">标签键</h2>
            <span className="rounded-full bg-[var(--yak-components-collapse-section-bg)] px-2 py-0.5 text-[11px] text-[var(--yak-components-muted-text)]">
              {keys.length} 个
            </span>
          </div>
          <div className="min-h-0 flex-1 overflow-auto p-2">
            {keys.length === 0 && !dictionary.isPending && (
              <div className="m-1 rounded-lg border border-dashed border-[var(--yak-components-control-border)] bg-[var(--yak-components-collapse-section-bg)] px-3 py-5 text-center text-xs leading-5 text-[var(--yak-components-muted-text)]">
                还没有标签键。
                <br />
                标签键是一个维度，例如「部门」或「敏感级别」。
              </div>
            )}
            {keys.map((key) => {
              const active = selectedKey?.id === key.id;
              return (
                <button
                  key={key.id}
                  type="button"
                  aria-current={active}
                  className={
                    "mb-1 flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-[13px] transition-colors " +
                    (active
                      ? "bg-[var(--yak-color-primary-subtle)] font-medium [color:var(--yak-color-primary)]"
                      : "text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]")
                  }
                  onClick={() => setSelectedKeyId(key.id)}
                >
                  <span
                    className={`flex size-7 shrink-0 items-center justify-center rounded-lg ${
                      active
                        ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                        : "bg-[var(--yak-components-collapse-section-bg)] text-[var(--yak-components-muted-text)]"
                    }`}
                  >
                    <Tags size={13} aria-hidden="true" />
                  </span>
                  <span className="min-w-0 flex-1 truncate">{key.name}</span>
                  <span className="shrink-0 rounded-full bg-[var(--yak-components-collapse-section-bg)] px-2 py-0.5 text-[11px] text-[var(--yak-components-muted-text)] [font-variant-numeric:tabular-nums]">
                    {key.values.length}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        <div className="flex min-h-0 flex-col overflow-hidden rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
          {selectedKey ? (
            <>
              <div className="flex min-h-14 flex-wrap items-center justify-between gap-2 border-b border-[var(--yak-components-panel-border)] px-4">
                <div className="flex min-w-0 items-center gap-2.5">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                    <Tags size={14} aria-hidden="true" />
                  </span>
                  <div className="min-w-0">
                    <h2 className="truncate text-sm font-bold text-[var(--yak-color-text-primary)]">
                      {selectedKey.name}
                    </h2>
                    <p className="mt-0.5 text-[11px] text-[var(--yak-components-muted-text)]">
                      {selectedKey.values.length} 个取值 · 已绑定资产{" "}
                      {selectedKey.values.reduce((sum, value) => sum + value.usageCount, 0)} 次
                    </p>
                  </div>
                </div>
                {canManage && (
                  <div className="flex items-center gap-1.5">
                    <Button
                      size="small"
                      variant="ghost"
                      onClick={() =>
                        setKeyDraft({
                          mode: "rename",
                          keyId: selectedKey.id,
                          name: selectedKey.name,
                        })
                      }
                    >
                      重命名
                    </Button>
                    <Button
                      size="small"
                      variant="ghost"
                      onClick={() => setConfirming({ kind: "key", id: selectedKey.id })}
                    >
                      删除
                    </Button>
                  </div>
                )}
              </div>
              <div className="min-h-0 flex-1 overflow-auto">
                <table className="w-full text-[13px]">
                  <thead>
                    <tr className="border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)] text-left text-xs text-[var(--yak-components-muted-text)]">
                      <th className="px-4 py-2.5 font-medium">取值</th>
                      <th className="w-48 px-3 py-2.5 font-medium">引用资产</th>
                      {canManage && <th className="w-32 px-3 py-2.5 font-medium">操作</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {selectedKey.values.map((value) => {
                      const maxUsage = Math.max(
                        1,
                        ...selectedKey.values.map((candidate) => candidate.usageCount),
                      );
                      return (
                        <tr
                          key={value.id}
                          className="border-b border-[var(--yak-datamap-divider-soft)] transition-colors last:border-b-0 hover:bg-[var(--yak-color-primary-subtle)]"
                        >
                          <td className="px-4 py-2.5">
                            <AccentBadge tone={accentToneOf(value.id)}>{value.name}</AccentBadge>
                          </td>
                          <td className="px-3 py-2.5">
                            <HeatBar value={value.usageCount} max={maxUsage} />
                          </td>
                          {canManage && (
                            <td className="px-3 py-2.5">
                              <div className="flex items-center gap-1">
                                <Button
                                  size="small"
                                  variant="ghost"
                                  onClick={() =>
                                    setValueDraft({
                                      mode: "rename",
                                      valueId: value.id,
                                      name: value.name,
                                    })
                                  }
                                >
                                  重命名
                                </Button>
                                <Button
                                  size="small"
                                  variant="ghost"
                                  onClick={() => setConfirming({ kind: "value", id: value.id })}
                                >
                                  删除
                                </Button>
                              </div>
                            </td>
                          )}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                {selectedKey.values.length === 0 && (
                  <p className="px-4 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
                    这个键还没有取值，添加第一个取值后即可绑定到资产。
                  </p>
                )}
              </div>
              {canManage && (
                <div className="border-t border-[var(--yak-components-panel-border)] px-4 py-2.5">
                  <Button
                    size="small"
                    onClick={() =>
                      setValueDraft({ mode: "create", keyId: selectedKey.id, name: "" })
                    }
                  >
                    <Plus size={13} aria-hidden="true" />
                    新增取值
                  </Button>
                </div>
              )}
            </>
          ) : (
            <div className="flex flex-1 flex-col items-center justify-center gap-3 p-6 text-center">
              <span className="flex size-12 items-center justify-center rounded-2xl bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                <Tags size={20} aria-hidden="true" />
              </span>
              <p className="text-[13px] text-[var(--yak-components-muted-text)]">
                从左侧选择一个标签键，或先创建一个。
              </p>
              <Button size="small" onClick={() => navigate("/data-governance/data-map/catalog")}>
                去资产目录打标签
              </Button>
            </div>
          )}
        </div>
      </div>

      {keyDraft && (
        <KeyDialog
          draft={keyDraft}
          pending={create.isPending || rename.isPending}
          onClose={() => setKeyDraft(null)}
          onSaved={(input) => {
            if (keyDraft.mode === "rename") {
              rename.mutate({ keyId: keyDraft.keyId, name: input.name });
              return;
            }
            create.mutate({ name: input.name, values: input.values });
          }}
        />
      )}

      {valueDraft && (
        <ValueDialog
          draft={valueDraft}
          pending={add.isPending || renameValueMutation.isPending}
          onClose={() => setValueDraft(null)}
          onSaved={(name) => {
            if (valueDraft.mode === "rename") {
              renameValueMutation.mutate({ valueId: valueDraft.valueId, name });
              return;
            }
            add.mutate({ keyId: valueDraft.keyId, name });
          }}
        />
      )}

      {confirming && (
        <Dialog
          open
          onOpenChange={(open) => {
            if (!open) setConfirming(null);
          }}
        >
          <DialogContent className="w-[400px]">
            <DialogTitle className="text-sm font-medium">删除确认</DialogTitle>
            <DialogDescription className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
              {confirming.kind === "key"
                ? "删除标签键会同时删除它的全部取值；仍在被资产引用的取值会阻止删除。"
                : "仍在被资产引用的标签值不能删除，请先在资产上解除引用。"}
            </DialogDescription>
            <div className="mt-4 flex justify-end gap-2">
              <Button size="small" onClick={() => setConfirming(null)}>
                取消
              </Button>
              <Button
                size="small"
                variant="danger"
                loading={removeKey.isPending || removeValue.isPending}
                onClick={() => {
                  if (confirming.kind === "key") removeKey.mutate(confirming.id);
                  else removeValue.mutate(confirming.id);
                }}
              >
                确认删除
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      )}

      {batchOpen && (
        <BatchBindingDialog
          keys={keys}
          onClose={() => setBatchOpen(false)}
          onApplied={(affected) => {
            setBatchOpen(false);
            refresh();
            toast.success(`已更新 ${affected} 条标签绑定`);
          }}
        />
      )}
    </div>
  );
}
