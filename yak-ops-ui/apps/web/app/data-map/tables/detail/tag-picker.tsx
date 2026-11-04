import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";

import { dataMapFailureMessage } from "@/service/data-map/request";
import type { TagRef } from "@/service/data-map/types";
import { replaceTableTags, tagFailureMessage } from "@/service/tags";

import AccentBadge, { accentToneOf } from "../../shared/accent-badge";
import { dataMapKeys } from "../../shared/keys";
import { useTagDictionary } from "../../shared/lookups";
import { useDataMapScope } from "../../shared/scope";

interface Props {
  tableId: string;
  tags: TagRef[];
}

type SaveState = "idle" | "saving" | "saved" | "failed";

/** Groups the asset's tag values by their key, keeping the key order of the response. */
function groupTagsByKey(tags: TagRef[]) {
  const grouped = new Map<string, { keyName: string; values: TagRef[] }>();
  for (const tag of tags) {
    const entry = grouped.get(tag.keyId) ?? { keyName: tag.keyName, values: [] };
    entry.values.push(tag);
    grouped.set(tag.keyId, entry);
  }
  return [...grouped].map(([keyId, entry]) => ({ keyId, ...entry }));
}

/**
 * Inline tag selector of one asset. The common tag-picker pattern: the current values sit in the
 * row, clicking the row (or the dashed + button) opens an anchored panel, and every click saves
 * immediately through the replace-set endpoint. There is deliberately no modal and no
 * cancel/save pair — each toggle is an independent, optimistic save with rollback on failure.
 */
export default function TagPicker({ tableId, tags }: Props) {
  const { scope } = useDataMapScope();
  const queryClient = useQueryClient();
  const dictionary = useTagDictionary();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [selected, setSelected] = useState<string[]>(() => tags.map((tag) => tag.valueId));
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [failure, setFailure] = useState("");
  const rootRef = useRef<HTMLDivElement | null>(null);
  const searchRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setSelected(tags.map((tag) => tag.valueId));
  }, [tags]);

  useEffect(() => {
    if (!open) return;
    searchRef.current?.focus();
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  const save = useMutation({
    mutationFn: (valueIds: string[]) => replaceTableTags(tableId, valueIds),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: dataMapKeys.tagDictionary(scope) });
    void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "tables"] });
    void queryClient.invalidateQueries({ queryKey: dataMapKeys.table(scope, tableId) });
  };

  const toggle = (valueId: string) => {
    if (save.isPending) return;
    const previous = selected;
    const next = previous.includes(valueId)
      ? previous.filter((id) => id !== valueId)
      : [...previous, valueId];
    setSelected(next);
    setSaveState("saving");
    setFailure("");
    save.mutate(next, {
      onSuccess: () => {
        invalidate();
        setSaveState("saved");
        window.setTimeout(() => {
          setSaveState((current) => (current === "saved" && !save.isPending ? "idle" : current));
        }, 1500);
      },
      onError: (error) => {
        setSelected(previous);
        setSaveState("failed");
        setFailure(tagFailureMessage(error, "保存失败，已还原。请稍后重试。"));
      },
    });
  };

  const groups = groupTagsByKey(tags);
  const keys = dictionary.data ?? [];
  const word = keyword.trim().toLowerCase();
  const filtered = keys
    .map((key) => ({
      ...key,
      values: key.values.filter(
        (value) =>
          !word || value.name.toLowerCase().includes(word) || key.name.toLowerCase().includes(word),
      ),
    }))
    .filter((key) => key.values.length > 0);

  const stateText =
    saveState === "saving"
      ? "保存中…"
      : saveState === "saved"
        ? "已保存"
        : saveState === "failed"
          ? failure
          : "";
  const busy = save.isPending;

  return (
    <div className="relative" ref={rootRef}>
      <div
        role="button"
        tabIndex={0}
        aria-expanded={open}
        onClick={() => !busy && setOpen((current) => !current)}
        onKeyDown={(event) => {
          if ((event.key === "Enter" || event.key === " ") && !busy) {
            event.preventDefault();
            setOpen((current) => !current);
          }
        }}
        className={`flex min-h-[30px] cursor-pointer flex-wrap items-center gap-2 rounded-[6px] px-2 py-1 ${
          open ? "bg-[var(--yak-color-hover)]" : "hover:bg-[var(--yak-color-hover)]"
        }`}
      >
        {groups.length === 0 ? (
          <span className="text-sm text-[var(--yak-components-muted-text)]">未打标签</span>
        ) : (
          groups.map((group) => (
            <span key={group.keyId} className="flex flex-wrap items-center gap-1.5">
              <span className="text-xs text-[var(--yak-components-muted-text)]">
                {group.keyName}
              </span>
              {group.values.map((tag) => (
                <AccentBadge
                  key={tag.valueId}
                  tone={accentToneOf(tag.valueId)}
                  onRemove={busy ? undefined : () => toggle(tag.valueId)}
                >
                  {tag.valueName}
                </AccentBadge>
              ))}
            </span>
          ))
        )}
        <span
          role="presentation"
          className="inline-flex h-6 items-center rounded-[4px] border border-dashed border-[var(--yak-components-control-border)] px-2 text-xs text-[var(--yak-components-muted-text)]"
        >
          {groups.length === 0 ? "+ 添加标签" : "+"}
        </span>
      </div>

      <p
        className={`mt-1 min-h-[18px] text-xs ${
          saveState === "failed"
            ? "text-[var(--yak-components-badge-danger-text)]"
            : "text-[var(--yak-components-muted-text)]"
        }`}
      >
        {stateText}
      </p>

      {open && (
        <div className="absolute left-0 top-full z-30 mt-1.5 flex w-[380px] flex-col overflow-hidden rounded-[8px] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] shadow-[0_6px_24px_rgba(29,33,41,0.12)]">
          <div className="border-b border-[var(--yak-components-panel-border)] p-2.5">
            <input
              ref={searchRef}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索标签值…"
              className="h-8 w-full rounded-[6px] border border-[var(--yak-components-control-border)] bg-transparent px-2.5 text-[13px] outline-none focus:border-[var(--yak-color-primary)]"
            />
          </div>

          <div className="max-h-[264px] overflow-auto py-1">
            {dictionary.isPending ? (
              <p className="px-4 py-6 text-center text-[13px] text-[var(--yak-components-muted-text)]">
                加载中…
              </p>
            ) : dictionary.isError ? (
              <p className="px-4 py-6 text-center text-[13px] text-[var(--yak-components-badge-danger-text)]">
                {dataMapFailureMessage(dictionary.error, "标签加载失败，请稍后重试。")}
              </p>
            ) : keys.length === 0 ? (
              <p className="px-4 py-6 text-center text-[13px] text-[var(--yak-components-muted-text)]">
                还没有标签键，先到
                <Link
                  to="/data-governance/tags"
                  className="mx-1 [color:var(--yak-color-primary)] hover:underline"
                >
                  标签管理
                </Link>
                创建。
              </p>
            ) : filtered.length === 0 ? (
              <p className="px-4 py-6 text-center text-[13px] text-[var(--yak-components-muted-text)]">
                没有匹配「{keyword.trim()}」的标签值
              </p>
            ) : (
              filtered.map((key) => (
                <section key={key.id}>
                  <h3 className="px-3.5 pb-1 pt-2 text-xs text-[var(--yak-components-muted-text)]">
                    {key.name}
                  </h3>
                  {key.values.map((value) => {
                    const on = selected.includes(value.id);
                    return (
                      <button
                        key={value.id}
                        type="button"
                        disabled={busy}
                        onClick={() => toggle(value.id)}
                        className={`flex w-full items-center gap-2 px-3.5 py-[7px] text-left text-[13.5px] disabled:cursor-not-allowed ${
                          on
                            ? "[color:var(--yak-color-primary)]"
                            : "text-[var(--yak-components-panel-text)]"
                        } hover:bg-[var(--yak-color-hover)]`}
                      >
                        <span className="w-4 text-[13px]">{on ? "✓" : ""}</span>
                        <span className="min-w-0 flex-1 truncate">{value.name}</span>
                        <span className="text-xs text-[var(--yak-components-muted-text)]">
                          {value.usageCount} 个资产
                        </span>
                      </button>
                    );
                  })}
                </section>
              ))
            )}
          </div>

          <div className="flex items-center justify-between border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-color-surface-muted)] px-3 py-2 text-xs text-[var(--yak-components-muted-text)]">
            <span>已选 {selected.length} 项</span>
            <span className="flex items-center gap-3">
              <Link
                to="/data-governance/tags"
                className="[color:var(--yak-color-primary)] hover:underline"
              >
                管理标签
              </Link>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="h-7 rounded-[6px] bg-[var(--yak-color-primary)] px-3.5 text-xs text-white hover:opacity-90"
              >
                完成
              </button>
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
