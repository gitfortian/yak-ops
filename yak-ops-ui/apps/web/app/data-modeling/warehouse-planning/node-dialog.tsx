import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Field,
  FieldError,
  FieldLabel,
  Form,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import {
  checkCode,
  getOwners,
  planningFailureField,
  saveNode,
} from "@/service/data-modeling/warehouse-planning";
import type {
  EditorDraft,
  NodeInput,
  OwnerOption,
  SavedNode,
} from "@/service/data-modeling/warehouse-planning/types";

const LABELS = { CATEGORY: "业务分类", DOMAIN: "数据域", PROCESS: "业务过程" };
const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";
const CODE = /^[a-z][a-z0-9_]{0,63}$/;

interface Props {
  draft: EditorDraft;
  onClose: () => void;
  onSaved: (node: SavedNode) => void;
}

export default function NodeDialog({ draft, onClose, onSaved }: Props) {
  const edited = draft.mode === "edit" ? draft.node : undefined;
  const kind = draft.mode === "edit" ? draft.node.kind : draft.kind;
  const id = useId();
  const [input, setInput] = useState<NodeInput>(() => ({
    name: edited?.name ?? "",
    code: edited?.code ?? "",
    ownerId: edited?.ownerId ?? "",
    description: edited?.description ?? null,
  }));
  const [errors, setErrors] = useState<Partial<Record<keyof NodeInput, string>>>({});
  const [owners, setOwners] = useState<OwnerOption[]>([]);
  const [ownerState, setOwnerState] = useState<"loading" | "ready" | "error">("loading");
  const [ownerVersion, setOwnerVersion] = useState(0);
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const busy = useRef(false);
  const active = useRef(true);
  const codeRequest = useRef<AbortController | null>(null);
  const latestCode = useRef(input.code);
  const formRef = useRef<HTMLFormElement>(null);

  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      codeRequest.current?.abort();
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    let live = true;
    setOwnerState("loading");
    void getOwners(controller.signal)
      .then((value) => {
        if (live) {
          setOwners(value);
          setOwnerState("ready");
        }
      })
      .catch(() => {
        if (live) setOwnerState("error");
      });
    return () => {
      live = false;
      controller.abort();
    };
  }, [ownerVersion]);

  const change = (field: keyof NodeInput, value: string) => {
    if (field === "code") {
      latestCode.current = value;
      codeRequest.current?.abort();
    }
    setInput((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  };
  const validateCode = async () => {
    codeRequest.current?.abort();
    const value = input.code;
    if (!CODE.test(value)) {
      setErrors((previous) => ({
        ...previous,
        code: "须以小写字母开头，仅含小写字母、数字或下划线",
      }));
      return;
    }
    const controller = new AbortController();
    codeRequest.current = controller;
    try {
      const result = await checkCode(value, edited, controller.signal);
      if (active.current && !controller.signal.aborted && latestCode.current === value) {
        setErrors((previous) => ({
          ...previous,
          code: result.available ? undefined : "英文编码已存在",
        }));
      }
    } catch {
      /* Transport reports failures; an unavailable check is never reported as available. */
    }
  };
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (busy.current || ownerState !== "ready" || conflict) return;
    const next: typeof errors = {};
    if (!input.name.trim()) next.name = "请输入中文名";
    if (input.name.trim().length > 128) next.name = "名称不能超过128个字符";
    if (!CODE.test(input.code)) next.code = "须以小写字母开头，仅含小写字母、数字或下划线";
    if (!owners.some((owner) => owner.userId === input.ownerId)) next.ownerId = "请选择有效负责人";
    if ((input.description?.length ?? 0) > 500) next.description = "描述不能超过500个字符";
    setErrors(next);
    if (Object.keys(next).length) {
      requestAnimationFrame(() =>
        formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
      );
      return;
    }
    codeRequest.current?.abort();
    busy.current = true;
    setSaving(true);
    try {
      const saved = await saveNode(draft, {
        ...input,
        name: input.name.trim(),
        description: input.description?.trim() || null,
      });
      if (active.current) {
        toast.success("保存成功");
        onSaved(saved);
      }
    } catch (error) {
      if (!active.current) return;
      if (planningFailureField(error) === "code") {
        setErrors((previous) => ({ ...previous, code: "英文编码已存在" }));
      } else if (planningFailureField(error) === "ownerId") {
        setErrors((previous) => ({ ...previous, ownerId: "请选择当前工作空间有效成员" }));
        setOwnerVersion((value) => value + 1);
      } else if (planningFailureField(error) === "revision") setConflict(true);
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
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
      <DialogContent className="w-[600px] rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {edited ? "编辑" : "新建"}
          {LABELS[kind]}
        </DialogTitle>
        <Form
          ref={formRef}
          noValidate
          onSubmit={(event) => void submit(event)}
          className="space-y-3"
        >
          {draft.parent && (
            <Field className={FIELD_CLASS}>
              <FieldLabel htmlFor={id + "-parent"} className="pt-1" required>
                {kind === "PROCESS" ? "所属数据域" : "所属业务分类"}
              </FieldLabel>
              <Select
                disabled
                value={draft.parent.id}
                items={[{ value: draft.parent.id, label: draft.parent.name }]}
                size="small"
              >
                <SelectTrigger id={id + "-parent"} variant="outlined">
                  <SelectValue />
                </SelectTrigger>
              </Select>
            </Field>
          )}
          <Field className={FIELD_CLASS} invalid={Boolean(errors.name)}>
            <FieldLabel htmlFor={id + "-name"} required className="pt-1">
              中文名
            </FieldLabel>
            <div>
              <Input
                id={id + "-name"}
                value={input.name}
                maxLength={128}
                required
                disabled={saving}
                size="small"
                variant="outlined"
                aria-invalid={Boolean(errors.name) || undefined}
                onChange={(event) => change("name", event.target.value)}
              />
              <FieldError match={Boolean(errors.name)}>{errors.name}</FieldError>
            </div>
          </Field>
          <Field className={FIELD_CLASS} invalid={Boolean(errors.code)}>
            <FieldLabel htmlFor={id + "-code"} required className="pt-1">
              英文编码
            </FieldLabel>
            <div>
              <Input
                id={id + "-code"}
                value={input.code}
                maxLength={64}
                required
                disabled={saving}
                size="small"
                variant="outlined"
                aria-invalid={Boolean(errors.code) || undefined}
                onChange={(event) => change("code", event.target.value)}
                onBlur={() => void validateCode()}
              />
              <FieldError match={Boolean(errors.code)}>{errors.code}</FieldError>
            </div>
          </Field>
          <Field className={FIELD_CLASS} invalid={Boolean(errors.ownerId)}>
            <FieldLabel htmlFor={id + "-owner"} required className="pt-1">
              负责人
            </FieldLabel>
            <div>
              <Select
                value={input.ownerId || null}
                size="small"
                disabled={saving || ownerState !== "ready"}
                items={owners.map((owner) => ({ value: owner.userId, label: owner.displayName }))}
                onValueChange={(value) => change("ownerId", value ?? "")}
              >
                <SelectTrigger
                  id={id + "-owner"}
                  variant="outlined"
                  aria-invalid={Boolean(errors.ownerId) || undefined}
                >
                  <SelectValue placeholder={ownerState === "loading" ? "加载中" : "请选择负责人"} />
                </SelectTrigger>
                <SelectContent>
                  {owners.map((owner) => (
                    <SelectItem key={owner.userId} value={owner.userId}>
                      <SelectItemText>{owner.displayName}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError match={Boolean(errors.ownerId)}>{errors.ownerId}</FieldError>
              {ownerState === "error" && (
                <Button size="small" onClick={() => setOwnerVersion((value) => value + 1)}>
                  重新加载负责人
                </Button>
              )}
            </div>
          </Field>
          <Field className={FIELD_CLASS} invalid={Boolean(errors.description)}>
            <FieldLabel htmlFor={id + "-description"} className="pt-1">
              描述
            </FieldLabel>
            <div>
              <Textarea
                id={id + "-description"}
                value={input.description ?? ""}
                disabled={saving}
                size="small"
                variant="outlined"
                rows={3}
                maxLength={500}
                onValueChange={(value) => change("description", String(value ?? ""))}
              />
              <FieldError match={Boolean(errors.description)}>{errors.description}</FieldError>
            </div>
          </Field>
          {conflict && <Alert>数据已变更。请保留所需内容，关闭弹窗后刷新再试。</Alert>}
          <div className="flex justify-end gap-2 border-t border-[var(--yak-components-panel-border)] pt-4">
            <Button size="small" disabled={saving} onClick={onClose}>
              取消
            </Button>
            <Button
              type="submit"
              size="small"
              variant="primary"
              loading={saving}
              disabled={ownerState !== "ready" || conflict}
            >
              保存
            </Button>
          </div>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
