import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
  Textarea,
} from "@yak-ops/yak-ui";
import { useState } from "react";

/** Create or rename request of one key; create may seed the first values, one per line. */
export type KeyDraft =
  | { mode: "create"; name: string; values: string }
  | { mode: "rename"; keyId: string; name: string };

interface Props {
  draft: KeyDraft;
  pending: boolean;
  onClose: () => void;
  onSaved: (input: { name: string; values: string[] }) => void;
}

export default function KeyDialog({ draft, pending, onClose, onSaved }: Props) {
  const [name, setName] = useState(draft.name);
  const [values, setValues] = useState(draft.mode === "create" ? draft.values : "");

  const parsedValues = values
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  const nameValid = name.trim().length > 0;

  return (
    <Dialog open disablePointerDismissal onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogContent className="w-[440px]">
        <DialogTitle className="text-sm font-medium">
          {draft.mode === "create" ? "新建标签键" : "重命名标签键"}
        </DialogTitle>
        <DialogDescription className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
          标签键是一个维度，名称在工作空间内唯一，最多 64 个字符。
        </DialogDescription>

        <div className="mt-4 space-y-3">
          <Input
            size="small"
            variant="outlined"
            value={name}
            placeholder="标签键名称，例如 部门"
            aria-label="标签键名称"
            onChange={(event) => setName(event.target.value)}
          />
          {draft.mode === "create" && (
            <Textarea
              rows={4}
              value={values}
              placeholder="可选：初始取值，每行一个，例如&#10;电商&#10;零售"
              aria-label="初始取值"
              onValueChange={setValues}
            />
          )}
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={pending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            loading={pending}
            disabled={!nameValid}
            onClick={() => onSaved({ name: name.trim(), values: parsedValues })}
          >
            保存
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
