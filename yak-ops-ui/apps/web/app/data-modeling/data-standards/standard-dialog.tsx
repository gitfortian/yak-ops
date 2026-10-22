import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Field,
  FieldError,
  FieldLabel,
  FieldRequiredMark,
  Form,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { useAuth } from "@/hooks/use-auth";
import {
  checkCodeUnique,
  getOwners,
  saveStandard,
  standardFailureField,
} from "@/service/data-modeling/data-standards";
import {
  DATA_TYPES,
  SECURITY_LABEL,
  SECURITY_LEVELS,
  type OwnerOption,
  type StandardDraft,
  type StandardInput,
} from "@/service/data-modeling/data-standards/types";
import { FIELD_CLASS, InputField, SelectField } from "./form-fields";
import { STANDARD_CODE, validateStandard } from "./validation";

export default function StandardDialog(props: {
  draft: StandardDraft;
  onClose: () => void;
  onSaved: () => void;
}) {
  const edited = props.draft.mode === "edit" ? props.draft.standard : undefined;
  const category =
    props.draft.mode === "edit" ? props.draft.standard.categoryPath : props.draft.category.path;
  const { currentUser } = useAuth();
  const [input, setInput] = useState<StandardInput>(() => ({
    cnName: edited?.cnName ?? "",
    enCode: edited?.enCode ?? "",
    dataType: edited?.dataType ?? "STRING",
    lengthPrecision: edited?.lengthPrecision ?? null,
    isNotNull: edited?.isNotNull ?? false,
    securityLevel: edited?.securityLevel ?? "INTERNAL",
    ownerId: edited?.ownerId ?? currentUser?.id ?? "",
    businessCaliber: edited?.businessCaliber ?? null,
  }));
  const [errors, setErrors] = useState<Partial<Record<keyof StandardInput, string>>>({});
  const [owners, setOwners] = useState<OwnerOption[]>([]);
  const [ownerState, setOwnerState] = useState<"loading" | "ready" | "error">("loading");
  const [ownerVersion, setOwnerVersion] = useState(0);
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [checkingCode, setCheckingCode] = useState(false);
  const active = useRef(true),
    busy = useRef(false);
  const codeRequest = useRef<AbortController | null>(null);
  const latestCode = useRef(input.enCode);
  latestCode.current = input.enCode;
  const form = useRef<HTMLFormElement>(null);
  const id = useId();
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      codeRequest.current?.abort();
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    setOwnerState("loading");
    void getOwners(controller.signal)
      .then((rows) => {
        if (current) {
          setOwners(rows);
          setOwnerState("ready");
        }
      })
      .catch(() => {
        if (current) setOwnerState("error");
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [ownerVersion]);
  const change = <K extends keyof StandardInput>(key: K, value: StandardInput[K]) => {
    setInput((previous) => ({ ...previous, [key]: value }));
    setErrors((previous) => ({ ...previous, [key]: undefined }));
    if (key === "enCode") {
      codeRequest.current?.abort();
      setCheckingCode(false);
    }
  };
  const validateCode = async () => {
    const value = input.enCode;
    codeRequest.current?.abort();
    if (!STANDARD_CODE.test(value)) {
      setErrors((previous) => ({
        ...previous,
        enCode: "须以小写字母开头，仅含小写字母、数字或下划线，最多64位",
      }));
      return;
    }
    const controller = new AbortController();
    codeRequest.current = controller;
    setCheckingCode(true);
    try {
      const result = await checkCodeUnique(value, edited?.id, controller.signal);
      if (active.current && !controller.signal.aborted && latestCode.current === value)
        setErrors((previous) => ({
          ...previous,
          enCode: result.available ? undefined : "英文编码已存在",
        }));
    } catch {
      if (active.current && !controller.signal.aborted && latestCode.current === value)
        setErrors((previous) => ({ ...previous, enCode: "编码校验失败，请重试" }));
    } finally {
      if (active.current && !controller.signal.aborted) setCheckingCode(false);
    }
  };
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy.current || conflict || ownerState !== "ready") return;
    const next = validateStandard(input, owners);
    setErrors(next);
    if (Object.keys(next).length) {
      requestAnimationFrame(() =>
        form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(),
      );
      return;
    }
    codeRequest.current?.abort();
    setCheckingCode(false);
    busy.current = true;
    setSaving(true);
    try {
      await saveStandard(props.draft, {
        ...input,
        cnName: input.cnName.trim(),
        lengthPrecision: input.lengthPrecision || null,
        businessCaliber: input.businessCaliber?.trim() || null,
      });
      if (active.current) {
        toast.success("保存成功");
        props.onSaved();
      }
    } catch (failure) {
      if (active.current) {
        const field = standardFailureField(failure);
        if (field === "revision") setConflict(true);
        else if (field === "enCode")
          setErrors((previous) => ({ ...previous, enCode: "英文编码已存在或不符合规范" }));
        else if (field === "lengthPrecision")
          setErrors((previous) => ({ ...previous, lengthPrecision: "长度或精度与数据类型不匹配" }));
        else if (field === "ownerId") {
          setErrors((previous) => ({ ...previous, ownerId: "请选择当前工作空间有效成员" }));
          setOwnerVersion((value) => value + 1);
        }
      }
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
    }
  };
  const ownerItems = owners.map((owner) => ({
    value: owner.userId,
    label: owner.displayName,
    disabled: false,
  }));
  if (edited && !owners.some((owner) => owner.userId === edited.ownerId))
    ownerItems.push({
      value: edited.ownerId,
      label: (edited.ownerName ?? edited.ownerId) + "（已不可选）",
      disabled: true,
    });
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !busy.current) props.onClose();
      }}
    >
      <DialogContent className="w-[640px] rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {edited ? "编辑标准" : "新建标准"}
        </DialogTitle>
        <Form ref={form} noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <InputField id={id + "-category"} label="所属分类" value={category} required disabled />
          <InputField
            id={id + "-name"}
            label="中文名称"
            value={input.cnName}
            required
            maxLength={128}
            disabled={saving}
            error={errors.cnName}
            onChange={(value) => change("cnName", value)}
          />
          <InputField
            id={id + "-code"}
            label="英文编码"
            value={input.enCode}
            required
            maxLength={64}
            disabled={saving}
            error={errors.enCode}
            onChange={(value) => change("enCode", value)}
            onBlur={() => void validateCode()}
          />
          {checkingCode && (
            <p role="status" className="pl-[116px] text-xs text-[#667085]">
              编码校验中
            </p>
          )}
          <SelectField
            id={id + "-type"}
            label="数据类型"
            value={input.dataType}
            disabled={saving}
            items={DATA_TYPES.map((value) => ({ value, label: value }))}
            onChange={(value) => {
              const type = DATA_TYPES.find((item) => item === value);
              if (type) {
                change("dataType", type);
                change("lengthPrecision", null);
              }
            }}
          />
          <InputField
            id={id + "-precision"}
            label="长度/精度"
            value={input.lengthPrecision ?? ""}
            maxLength={32}
            disabled={saving || (input.dataType !== "STRING" && input.dataType !== "DECIMAL")}
            error={errors.lengthPrecision}
            onChange={(value) => change("lengthPrecision", value || null)}
          />
          <div className={FIELD_CLASS}>
            <span id={id + "-nullable-label"} className="pt-1 text-xs font-medium">
              是否非空
              <FieldRequiredMark />
            </span>
            <div
              role="radiogroup"
              aria-labelledby={id + "-nullable-label"}
              aria-required
              className="flex min-h-7 items-center gap-5 text-xs"
            >
              {[true, false].map((value) => (
                <label key={String(value)} className="flex items-center gap-2">
                  <input
                    type="radio"
                    name={id + "-nullable"}
                    required
                    disabled={saving}
                    checked={input.isNotNull === value}
                    className="size-3.5 accent-[var(--yak-color-primary)]"
                    onChange={() => change("isNotNull", value)}
                  />
                  {value ? "是" : "否"}
                </label>
              ))}
            </div>
          </div>
          <SelectField
            id={id + "-security"}
            label="安全等级"
            value={input.securityLevel}
            disabled={saving}
            items={SECURITY_LEVELS.map((value) => ({ value, label: SECURITY_LABEL[value] }))}
            onChange={(value) => {
              const level = SECURITY_LEVELS.find((item) => item === value);
              if (level) change("securityLevel", level);
            }}
          />
          <SelectField
            id={id + "-owner"}
            label="负责人"
            value={input.ownerId}
            items={ownerItems}
            error={errors.ownerId}
            disabled={saving || ownerState !== "ready"}
            onChange={(value) => change("ownerId", value)}
          />
          {ownerState === "error" && (
            <Alert>
              负责人加载失败
              <Button size="small" onClick={() => setOwnerVersion((value) => value + 1)}>
                重试
              </Button>
            </Alert>
          )}
          <Field className={FIELD_CLASS} invalid={Boolean(errors.businessCaliber)}>
            <FieldLabel htmlFor={id + "-caliber"} className="pt-1">
              业务口径
            </FieldLabel>
            <div>
              <Textarea
                id={id + "-caliber"}
                size="small"
                variant="outlined"
                value={input.businessCaliber ?? ""}
                rows={3}
                maxLength={2000}
                disabled={saving}
                onValueChange={(value) => change("businessCaliber", String(value ?? "") || null)}
              />
              <FieldError match={Boolean(errors.businessCaliber)}>
                {errors.businessCaliber}
              </FieldError>
            </div>
          </Field>
          {conflict && <Alert>数据已变更，请关闭弹窗并刷新后重试。</Alert>}
          <div className="flex justify-end gap-2 pt-2">
            <Button size="small" disabled={saving} onClick={props.onClose}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              type="submit"
              loading={saving}
              disabled={conflict || ownerState !== "ready"}
            >
              保存
            </Button>
          </div>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
