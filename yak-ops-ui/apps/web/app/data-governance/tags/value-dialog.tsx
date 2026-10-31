import { Button, Dialog, DialogContent, DialogTitle, Input } from "@yak-ops/yak-ui";
import { useState } from "react";

/** Add or rename request of one value inside one key. */
export type ValueDraft =
  | { mode: "create"; keyId: string; name: string }
  | { mode: "rename"; valueId: string; name: string };

interface Props {
  draft: ValueDraft;
  pending: boolean;
  onClose: () => void;
  onSaved: (name: string) => void;
}

export default function ValueDialog({ draft, pending, onClose, onSaved }: Props) {
  const [name, setName] = useState(draft.name);
  const nameValid = name.trim().length > 0;

  return (
    <Dialog open disablePointerDismissal onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogContent className="w-[400px]">
        <DialogTitle className="text-[15px] font-semibold">
          {draft.mode === "create" ? "新增标签值" : "重命名标签值"}
        </DialogTitle>

        <div className="mt-4">
          <Input
            size="small"
            variant="outlined"
            value={name}
            placeholder="标签值名称，例如 电商"
            aria-label="标签值名称"
            onChange={(event) => setName(event.target.value)}
          />
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
            onClick={() => onSaved(name.trim())}
          >
            保存
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
