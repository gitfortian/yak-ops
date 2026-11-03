import { useState } from "react";
import { Button, Dialog, DialogContent, DialogTitle, Input } from "@yak-ops/yak-ui";
import { replaceFieldSynonyms } from "@/service/data-modeling/semantic";

const MAX_TERMS = 5;
const MAX_TERM_LENGTH = 20;

/**
 * 字段同义词编辑器。同义词按 `modelId:fieldCode` 存储，与模型保存链路解耦：改完立即生效，
 * 不会因为用户取消模型编辑而丢失。
 */
export function FieldSynonymsDialog({
  modelId,
  fieldCode,
  fieldName,
  terms,
  disabled,
  onClose,
  onSaved,
}: {
  modelId: string;
  fieldCode: string;
  fieldName: string;
  terms: string[];
  disabled?: boolean;
  onClose: () => void;
  onSaved: (terms: string[]) => void;
}) {
  const [current, setCurrent] = useState<string[]>(terms);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const add = () => {
    const term = draft.trim();
    if (!term) return;
    if (term.length > MAX_TERM_LENGTH) {
      setFailure(`单个同义词不能超过 ${MAX_TERM_LENGTH} 个字符`);
      return;
    }
    if (current.some((item) => item.toLowerCase() === term.toLowerCase())) {
      setFailure("同义词不能重复");
      return;
    }
    if (current.length >= MAX_TERMS) {
      setFailure(`每个字段最多 ${MAX_TERMS} 个同义词`);
      return;
    }
    setCurrent((previous) => [...previous, term]);
    setDraft("");
    setFailure(null);
  };

  const save = async () => {
    setBusy(true);
    setFailure(null);
    try {
      const saved = await replaceFieldSynonyms(modelId, fieldCode, current);
      onSaved(saved);
    } catch (error) {
      setFailure(error instanceof Error ? error.message : "保存失败，请稍后重试");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-md">
        <DialogTitle>字段同义词 · {fieldName}</DialogTitle>
        <div className="space-y-3">
          <p className="text-xs leading-5 text-[var(--yak-components-field-label)]">
            同义词用于业务检索与语义导出，不改变字段编码。未保存模型也可以单独维护。
          </p>
          <div className="flex flex-wrap gap-1.5">
            {current.length === 0 && (
              <span className="text-xs text-[var(--yak-components-field-label)]">
                还没有同义词。
              </span>
            )}
            {current.map((term) => (
              <span
                key={term}
                className="inline-flex items-center gap-1 rounded-full border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-badge-info-bg)] px-2 py-0.5 text-xs"
              >
                {term}
                <button
                  type="button"
                  disabled={busy || disabled}
                  aria-label={`删除同义词 ${term}`}
                  className="text-[var(--yak-components-field-label)] hover:text-[var(--yak-components-badge-danger-text)]"
                  onClick={() => setCurrent((previous) => previous.filter((item) => item !== term))}
                >
                  ×
                </button>
              </span>
            ))}
          </div>
          <div className="flex gap-2">
            <Input
              value={draft}
              maxLength={MAX_TERM_LENGTH}
              placeholder="例如：客户编号"
              disabled={busy || disabled || current.length >= MAX_TERMS}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  add();
                }
              }}
            />
            <Button
              size="small"
              disabled={busy || disabled || current.length >= MAX_TERMS}
              onClick={add}
            >
              添加
            </Button>
          </div>
          {failure && (
            <p className="rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-badge-danger-bg)] p-2.5 text-xs text-[var(--yak-components-badge-danger-text)]">
              {failure}
            </p>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-[var(--yak-components-panel-border)] px-4 py-3">
          <Button onClick={onClose}>取消</Button>
          <Button variant="primary" disabled={busy || disabled} onClick={save}>
            {busy ? "保存中…" : "保存同义词"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
