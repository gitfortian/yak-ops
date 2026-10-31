import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Field,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
} from "@yak-ops/yak-ui";
import { useState, type ReactNode } from "react";

import { useOwnerOptions } from "@/app/data-map/shared/lookups";

/** 提交一个批量命令的弹框载荷（不含 issueIds，由调用方附带）。 */
export interface CommandDialogProps {
  pending: boolean;
  onClose: () => void;
  onSubmit: (input: { ownerId?: string; ignoreUntil?: string; note?: string }) => void;
}

const addDays = (days: number) => new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);

function DialogShell({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="w-[420px]">
        <DialogTitle className="text-[15px] font-semibold">{title}</DialogTitle>
        <div className="mt-4">{children}</div>
      </DialogContent>
    </Dialog>
  );
}

function DialogActions({
  onClose,
  pending,
  disabled,
  onSubmit,
  confirmText,
}: {
  onClose: () => void;
  pending: boolean;
  disabled?: boolean;
  onSubmit: () => void;
  confirmText: string;
}) {
  return (
    <div className="mt-5 flex justify-end gap-2">
      <Button size="small" disabled={pending} onClick={onClose}>
        取消
      </Button>
      <Button
        size="small"
        variant="primary"
        loading={pending}
        disabled={disabled}
        onClick={onSubmit}
      >
        {confirmText}
      </Button>
    </div>
  );
}

/** 指派负责人：候选为工作空间的活跃成员。 */
export function AssignDialog({ pending, onClose, onSubmit }: CommandDialogProps) {
  const owners = useOwnerOptions();
  const [ownerId, setOwnerId] = useState("");
  const options = (owners.data ?? []).map((owner) => ({
    value: owner.userId,
    label: owner.displayName,
  }));

  return (
    <DialogShell title="指派负责人" onClose={onClose}>
      <Field>
        <FieldLabel>负责人</FieldLabel>
        <Select
          size="small"
          items={options}
          value={ownerId}
          onValueChange={(value) => setOwnerId(value ?? "")}
        >
          <SelectTrigger variant="outlined" className="w-full">
            <SelectValue placeholder="选择活跃成员" />
          </SelectTrigger>
          <SelectContent>
            {options.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                <SelectItemText>{option.label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>
      <DialogActions
        onClose={onClose}
        pending={pending}
        disabled={!ownerId}
        onSubmit={() => onSubmit({ ownerId })}
        confirmText="指派"
      />
    </DialogShell>
  );
}

/** 手动解决：备注可选；若问题仍存在，下一次完整扫描会重新打开。 */
export function ResolveDialog({ pending, onClose, onSubmit }: CommandDialogProps) {
  const [note, setNote] = useState("");
  return (
    <DialogShell title="标记为已解决" onClose={onClose}>
      <Field>
        <FieldLabel>备注（可选）</FieldLabel>
        <Textarea
          rows={3}
          maxLength={500}
          value={note}
          placeholder="记录处理方式，最多 500 字"
          onValueChange={setNote}
        />
      </Field>
      <DialogActions
        onClose={onClose}
        pending={pending}
        onSubmit={() => onSubmit({ note: note.trim() || undefined })}
        confirmText="解决"
      />
    </DialogShell>
  );
}

/** 忽略：必须给出 1–90 天内的未来日期。 */
export function IgnoreDialog({ pending, onClose, onSubmit }: CommandDialogProps) {
  const [until, setUntil] = useState(addDays(7));
  const min = addDays(1);
  const max = addDays(90);
  const valid = until >= min && until <= max;

  return (
    <DialogShell title="忽略问题" onClose={onClose}>
      <Field>
        <FieldLabel>忽略至（1–90 天）</FieldLabel>
        <Input
          type="date"
          size="small"
          variant="outlined"
          value={until}
          min={min}
          max={max}
          aria-label="忽略截止日期"
          onChange={(event) => setUntil(event.target.value)}
        />
      </Field>
      <DialogActions
        onClose={onClose}
        pending={pending}
        disabled={!valid}
        onSubmit={() => onSubmit({ ignoreUntil: `${until}T00:00:00` })}
        confirmText="忽略"
      />
    </DialogShell>
  );
}
