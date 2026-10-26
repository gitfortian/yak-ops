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
  Input,
  Select,
  SelectContent,
  SelectFooter,
  SelectItem,
  SelectItemText,
  SelectSearch,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { useAuth } from "@/hooks/use-auth";
import {
  checkCodeUnique,
  classifyStandardFailure,
  getLookups,
  getMeasureUnits,
  getOwners,
  getStandards,
  saveStandard,
} from "@/service/data-modeling/data-standards";
import {
  DATA_TYPES,
  SECURITY_LABEL,
  SECURITY_LEVELS,
  type OwnerOption,
  type StandardDraft,
  type StandardInput,
} from "@/service/data-modeling/data-standards/types";
import { FIELD_CLASS, InputField, SelectField } from "../shared/form-fields";
import { useOptions } from "./state";
import { STANDARD_CODE, decimalIntegerDigits, validateStandard } from "./validation";

const NONE = "NONE";

/** Splits the stored `p,s` pair so DECIMAL can be edited as two numbers. */
function pairOf(value: string | null) {
  const [precision = "", scale = ""] = (value ?? "").split(",");
  return { precision, scale };
}

export default function StandardDialog(props: {
  draft: StandardDraft;
  onClose: () => void;
  onSaved: () => void;
  onStaleCategory: () => void;
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
    defaultValue: edited?.defaultValue ?? null,
    parentStandardId: edited?.parentStandardId ?? null,
    lookupId: edited?.lookupId ?? null,
    measureUnitId: edited?.measureUnitId ?? null,
  }));
  const [errors, setErrors] = useState<Partial<Record<keyof StandardInput, string>>>({});
  const [owners, setOwners] = useState<OwnerOption[]>([]);
  const [ownerState, setOwnerState] = useState<"loading" | "ready" | "error">("loading");
  const [ownerVersion, setOwnerVersion] = useState(0);
  const [parentKeyword, setParentKeyword] = useState("");
  const [parentResults, setParentResults] = useState<{ value: string; label: string }[]>([]);
  const [parentLabel, setParentLabel] = useState<string | null>(() =>
    edited?.parentStandardId
      ? (edited.parentStandardEnCode ?? edited.parentStandardId) + "（当前父标准）"
      : null,
  );
  const [parentState, setParentState] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [optionVersion, setOptionVersion] = useState(0);
  const lookups = useOptions(
    (signal) =>
      getLookups({ scope: "KEYWORD", keyword: "", pageNo: 1, pageSize: 100 }, signal).then(
        (page) => page.bizData,
      ),
    optionVersion,
  );
  const units = useOptions(
    (signal) => getMeasureUnits(null, "", 1, 100, signal).then((page) => page.bizData),
    optionVersion,
  );
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const [stale, setStale] = useState(false);
  const [checkingCode, setCheckingCode] = useState(false);
  const active = useRef(true),
    busy = useRef(false),
    ownerTouched = useRef(false);
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
  useEffect(() => {
    // The session profile resolves asynchronously; fill the default only while the user has not chosen an owner.
    if (edited || ownerTouched.current || !currentUser?.id) return;
    setInput((previous) =>
      previous.ownerId ? previous : { ...previous, ownerId: currentUser.id },
    );
  }, [currentUser?.id, edited]);
  useEffect(() => {
    // Candidates are matched on the server, so the dropdown only queries once the user typed something.
    const keyword = parentKeyword.trim();
    if (!keyword) {
      setParentResults([]);
      setParentState("idle");
      return;
    }
    const controller = new AbortController();
    setParentState("loading");
    const timer = window.setTimeout(() => {
      void getStandards({ scope: "ALL", keyword, pageNo: 1, pageSize: 20 }, controller.signal)
        .then((page) => {
          if (!active.current || controller.signal.aborted) return;
          setParentResults(
            page.bizData
              .filter((row) => row.id !== edited?.id)
              .map((row) => ({ value: row.id, label: `${row.cnName}（${row.enCode}）` })),
          );
          setParentState("ready");
        })
        .catch(() => {
          if (active.current && !controller.signal.aborted) setParentState("error");
        });
    }, 250);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [parentKeyword, edited?.id]);
  const change = <K extends keyof StandardInput>(key: K, value: StandardInput[K]) => {
    if (key === "ownerId") ownerTouched.current = true;
    setInput((previous) => ({ ...previous, [key]: value }));
    setErrors((previous) => ({ ...previous, [key]: undefined }));
    if (key === "enCode") {
      codeRequest.current?.abort();
      setCheckingCode(false);
    }
  };
  // A DECIMAL pair is stored as `p,s`; an incomplete pair is kept verbatim so validation can name it.
  const setPair = (precision: string, scale: string) =>
    change(
      "lengthPrecision",
      precision.trim() || scale.trim() ? `${precision.trim()},${scale.trim()}` : null,
    );
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
    if (busy.current || conflict || stale || ownerState !== "ready") return;
    setFailure("");
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
        businessCaliber: input.businessCaliber?.trim() || null,
        defaultValue: input.defaultValue?.trim() || null,
      });
      if (active.current) {
        toast.success("保存成功");
        props.onSaved();
      }
    } catch (error) {
      if (active.current) {
        const result = classifyStandardFailure(error);
        if (result.kind === "field") {
          if (result.field === "enCode")
            setErrors((previous) => ({ ...previous, enCode: "英文编码已存在或不符合规范" }));
          else if (result.field === "lengthPrecision")
            setErrors((previous) => ({
              ...previous,
              lengthPrecision: "长度或精度与数据类型不匹配",
            }));
          else if (result.field === "parentStandardId")
            setErrors((previous) => ({
              ...previous,
              parentStandardId: "父标准不能是自身或形成循环",
            }));
          else {
            setErrors((previous) => ({ ...previous, ownerId: "请选择当前工作空间有效成员" }));
            setOwnerVersion((value) => value + 1);
          }
        } else if (result.kind === "conflict") setConflict(true);
        else if (result.kind === "missing") {
          setStale(true);
          setFailure("所属分类已不存在，请刷新后重试。");
          props.onStaleCategory();
        } else if (result.kind === "forbidden") {
          setStale(true);
          setFailure("无权在该分类下保存标准。");
        } else setFailure(result.kind === "unknown" ? result.message : "保存失败，请稍后重试");
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
  const pair = pairOf(input.lengthPrecision);
  const integerDigits = decimalIntegerDigits(input.lengthPrecision);
  const lookupItems = [
    { value: NONE, label: "不使用" },
    ...(lookups.data ?? []).map((row) => ({
      value: row.id,
      label: `${row.cnName}（${row.enCode}）`,
    })),
  ];
  if (edited?.lookupId && !lookupItems.some((item) => item.value === edited.lookupId))
    lookupItems.push({
      value: edited.lookupId,
      label: (edited.lookupCnName ?? edited.lookupEnCode ?? edited.lookupId) + "（不可选）",
    });
  const unitItems = [
    { value: NONE, label: "不使用" },
    ...(units.data ?? []).map((row) => ({
      value: row.id,
      label: `${row.cnName}（${row.enCode}）`,
    })),
  ];
  if (edited?.measureUnitId && !unitItems.some((item) => item.value === edited.measureUnitId))
    unitItems.push({
      value: edited.measureUnitId,
      label:
        (edited.measureUnitCnName ?? edited.measureUnitEnCode ?? edited.measureUnitId) +
        "（不可选）",
    });
  const parentItems = [{ value: NONE, label: "无" }, ...parentResults];
  // The chosen parent may fall outside the current result page; keep its label so the trigger still names it.
  if (
    input.parentStandardId &&
    parentLabel &&
    !parentResults.some((item) => item.value === input.parentStandardId)
  )
    parentItems.push({ value: input.parentStandardId, label: parentLabel });
  const parentStatus =
    parentState === "loading"
      ? "查找中…"
      : parentState === "error"
        ? "查找失败，请重试"
        : parentState === "ready" && !parentResults.length
          ? "没有匹配的字段标准"
          : "";
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !busy.current) props.onClose();
      }}
    >
      <DialogContent className="max-h-[86vh] w-[640px] overflow-auto rounded-xl p-5 shadow-none">
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
          {input.dataType === "DECIMAL" ? (
            <>
              <div className={FIELD_CLASS}>
                <span className="pt-1 text-xs font-medium">
                  精度
                  <FieldRequiredMark />
                </span>
                <div className="flex items-center gap-2">
                  <Input
                    id={id + "-precision"}
                    size="small"
                    variant="outlined"
                    value={pair.precision}
                    maxLength={2}
                    inputMode="numeric"
                    disabled={saving}
                    aria-label="总位数"
                    aria-invalid={Boolean(errors.lengthPrecision) || undefined}
                    placeholder="总位数，如18"
                    onChange={(event) => setPair(event.target.value, pair.scale)}
                  />
                  <Input
                    id={id + "-scale"}
                    size="small"
                    variant="outlined"
                    value={pair.scale}
                    maxLength={2}
                    inputMode="numeric"
                    disabled={saving}
                    aria-label="小数位"
                    aria-invalid={Boolean(errors.lengthPrecision) || undefined}
                    placeholder="小数位，如2"
                    onChange={(event) => setPair(pair.precision, event.target.value)}
                  />
                </div>
              </div>
              {errors.lengthPrecision ? (
                <p className="pl-[116px] text-xs text-[#d92d20]">{errors.lengthPrecision}</p>
              ) : integerDigits === null ? null : (
                <p className="pl-[116px] text-[11px] text-[#667085]">整数位 {integerDigits} 位</p>
              )}
            </>
          ) : (
            <InputField
              id={id + "-precision"}
              label="长度"
              value={input.lengthPrecision ?? ""}
              maxLength={32}
              disabled={saving || input.dataType !== "STRING"}
              error={errors.lengthPrecision}
              onChange={(value) => change("lengthPrecision", value || null)}
            />
          )}
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
          <Field className={FIELD_CLASS} invalid={Boolean(errors.parentStandardId)}>
            <FieldLabel htmlFor={id + "-parent"} className="pt-1">
              父标准
            </FieldLabel>
            <div>
              <Select
                value={input.parentStandardId ?? NONE}
                size="small"
                items={parentItems}
                disabled={saving}
                onOpenChange={(open) => {
                  if (!open) setParentKeyword("");
                }}
                onValueChange={(value) => {
                  const next = !value || value === NONE ? null : value;
                  setParentLabel(
                    next ? (parentItems.find((item) => item.value === next)?.label ?? null) : null,
                  );
                  change("parentStandardId", next);
                }}
              >
                <SelectTrigger
                  id={id + "-parent"}
                  variant="outlined"
                  aria-invalid={Boolean(errors.parentStandardId) || undefined}
                >
                  <SelectValue placeholder="请选择" />
                </SelectTrigger>
                <SelectContent
                  header={
                    <SelectSearch
                      value={parentKeyword}
                      maxLength={128}
                      autoFocus
                      placeholder="输入中文名称或英文编码查找"
                      aria-label="查找父标准"
                      onChange={(event) => setParentKeyword(event.target.value)}
                      onKeyDown={(event) => {
                        // Enter inside the popup must not submit the dialog form.
                        if (event.key === "Enter") {
                          event.preventDefault();
                          event.stopPropagation();
                        }
                      }}
                    />
                  }
                  footer={
                    parentStatus ? (
                      <SelectFooter
                        className={
                          "min-h-0 px-3 py-1.5 text-[11px] " +
                          (parentState === "error" ? "text-[#d92d20]" : "text-[#667085]")
                        }
                      >
                        {parentStatus}
                      </SelectFooter>
                    ) : undefined
                  }
                >
                  {parentItems.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      <SelectItemText>{item.label}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError match={Boolean(errors.parentStandardId)}>
                {errors.parentStandardId}
              </FieldError>
            </div>
          </Field>
          <SelectField
            id={id + "-lookup"}
            label="标准代码"
            required={false}
            value={input.lookupId ?? NONE}
            items={lookupItems}
            disabled={saving || lookups.status === "loading"}
            hint="引用标准代码约束该字段的取值范围；本标准的编码与名称不受影响"
            onChange={(value) => change("lookupId", value === NONE ? null : value)}
          />
          <SelectField
            id={id + "-unit"}
            label="度量单位"
            required={false}
            value={input.measureUnitId ?? NONE}
            items={unitItems}
            disabled={saving || units.status === "loading"}
            onChange={(value) => change("measureUnitId", value === NONE ? null : value)}
          />
          {(lookups.status === "error" || units.status === "error") && (
            <Alert>
              标准代码或度量单位加载失败
              <Button size="small" onClick={() => setOptionVersion((value) => value + 1)}>
                重试
              </Button>
            </Alert>
          )}
          <InputField
            id={id + "-default"}
            label="业务预设值"
            value={input.defaultValue ?? ""}
            maxLength={2048}
            disabled={saving}
            error={errors.defaultValue}
            hint="仅作为业务预设值保存，不写入建表语句的默认值"
            onChange={(value) => change("defaultValue", value || null)}
          />
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
          {failure && <Alert>{failure}</Alert>}
          <div className="flex justify-end gap-2 pt-2">
            <Button size="small" disabled={saving} onClick={props.onClose}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              type="submit"
              loading={saving}
              disabled={conflict || stale || ownerState !== "ready"}
            >
              保存
            </Button>
          </div>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
