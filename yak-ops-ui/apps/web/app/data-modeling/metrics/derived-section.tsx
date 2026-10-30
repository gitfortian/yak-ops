import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Field,
  FieldError,
  FieldLabel,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  Textarea,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { ArrowUp, X } from "lucide-react";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import {
  checkDerivedAvailability,
  classifyMetricFailure,
  getAtomics,
  getDerived,
  getTimePeriods,
  getModifiers,
  saveDerived,
} from "@/service/data-modeling/metrics";
import type {
  MetricDeleteTarget,
  MetricDerived,
  MetricDerivedInput,
} from "@/service/data-modeling/metrics/types";
import { previewComposition } from "./composition";
import { FIELD_CLASS } from "../shared/form-fields";
import MetricToolbar from "./metric-toolbar";
import { useClampPage, useMetricOptions, useMetricPage } from "./page-state";

function ModifierPicker(props: {
  items: { id: string; label: string }[];
  disabled: boolean;
  selected: string[];
  onAdd: (id: string) => void;
  onRemove: (index: number) => void;
  onMoveUp: (index: number) => void;
}) {
  return (
    <div className="space-y-2">
      <Select
        value={null}
        size="small"
        items={props.items
          .filter((item) => !props.selected.includes(item.id))
          .map((item) => ({ value: item.id, label: item.label }))}
        disabled={props.disabled}
        onValueChange={(value) => {
          if (value) props.onAdd(value);
        }}
      >
        <SelectTrigger variant="outlined" aria-label="追加修饰词">
          <SelectValue placeholder="选择修饰词追加" />
        </SelectTrigger>
        <SelectContent>
          {props.items
            .filter((item) => !props.selected.includes(item.id))
            .map((item) => (
              <SelectItem key={item.id} value={item.id}>
                <SelectItemText>{item.label}</SelectItemText>
              </SelectItem>
            ))}
        </SelectContent>
      </Select>
      {props.selected.length > 0 && (
        <ul className="space-y-1" aria-label="已选修饰词顺序">
          {props.selected.map((modifierId, index) => {
            const item = props.items.find((entry) => entry.id === modifierId);
            return (
              <li
                key={modifierId}
                className="flex items-center justify-between gap-2 rounded border border-[var(--yak-components-panel-border)] px-2 py-1 text-xs"
              >
                <span className="min-w-0 truncate">
                  {index + 1}. {item?.label ?? modifierId}
                </span>
                <span className="flex shrink-0 items-center gap-1">
                  <Button
                    size="small"
                    variant="ghost"
                    aria-label={"上移" + (item?.label ?? "")}
                    disabled={props.disabled || index === 0}
                    className="px-1"
                    onClick={() => props.onMoveUp(index)}
                  >
                    <ArrowUp size={12} />
                  </Button>
                  <Button
                    size="small"
                    variant="ghost"
                    aria-label={"移除" + (item?.label ?? "")}
                    disabled={props.disabled}
                    className="px-1 text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-components-danger-text)]"
                    onClick={() => props.onRemove(index)}
                  >
                    <X size={12} />
                  </Button>
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function DerivedDialog(props: {
  draft: { mode: "create" } | { mode: "edit"; node: MetricDerived };
  onClose: () => void;
  onSaved: () => void;
}) {
  const edited = props.draft.mode === "edit" ? props.draft.node : undefined;
  const [input, setInput] = useState<MetricDerivedInput>(() => ({
    atomicMetricId: edited?.atomicMetricId ?? "",
    periodId: edited?.periodId ?? "",
    modifierIds: edited?.modifiers.map((part) => part.modifierId) ?? [],
    ownerId: edited?.ownerId ?? null,
    description: edited?.description ?? null,
  }));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const [taken, setTaken] = useState(false);
  const [optionVersion, setOptionVersion] = useState(0);
  const active = useRef(true),
    busy = useRef(false),
    checkSequence = useRef(0);
  const id = useId();
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const atomics = useMetricOptions(
    (signal) => getAtomics({ pageNo: 1, pageSize: 100 }, signal).then((page) => page.bizData),
    optionVersion,
  );
  const periods = useMetricOptions(
    (signal) => getTimePeriods({ pageNo: 1, pageSize: 100 }, signal).then((page) => page.bizData),
    optionVersion,
  );
  const modifiers = useMetricOptions(
    (signal) => getModifiers({ pageNo: 1, pageSize: 100 }, signal).then((page) => page.bizData),
    optionVersion,
  );
  const atomic = (atomics.data ?? []).find((row) => row.id === input.atomicMetricId);
  const period = (periods.data ?? []).find((row) => row.id === input.periodId);
  // The option lists hold one page of 100. A saved metric may cite parts beyond it, and a blank
  // preview would hide the composed code the record already carries, so a part that is still the
  // one that was saved falls back to its stored code and name.
  const savedAtomic = input.atomicMetricId === edited?.atomicMetricId ? edited : undefined;
  const savedPeriod = input.periodId === edited?.periodId ? edited : undefined;
  const partCode = (modifierId: string) =>
    (modifiers.data ?? []).find((row) => row.id === modifierId)?.enCode ??
    edited?.modifiers.find((part) => part.modifierId === modifierId)?.modifierEnCode;
  const partName = (modifierId: string) =>
    (modifiers.data ?? []).find((row) => row.id === modifierId)?.cnName ??
    edited?.modifiers.find((part) => part.modifierId === modifierId)?.modifierCnName;
  const preview = previewComposition({
    periodCode: period?.enCode ?? savedPeriod?.periodEnCode,
    modifierCodes: input.modifierIds.map(partCode).filter((code): code is string => Boolean(code)),
    atomicCode: atomic?.enCode ?? savedAtomic?.atomicEnCode,
    periodName: period?.cnName ?? savedPeriod?.periodCnName,
    modifierNames: input.modifierIds.map(partName).filter((name): name is string => Boolean(name)),
    atomicName: atomic?.cnName ?? savedAtomic?.atomicCnName,
  });
  // Availability re-answers on every complete part set; a stale response never wins.
  useEffect(() => {
    if (!preview) return;
    const sequence = ++checkSequence.current;
    const controller = new AbortController();
    void checkDerivedAvailability(
      input.atomicMetricId,
      input.periodId,
      input.modifierIds,
      edited?.id,
      controller.signal,
    )
      .then((result) => {
        if (active.current && sequence === checkSequence.current) setTaken(!result.available);
      })
      .catch(() => {
        // The save path answers authoritatively when the check fails.
      });
    return () => controller.abort();
  }, [preview?.composed.enCode, edited?.id]);
  const change = (patch: Partial<MetricDerivedInput>) =>
    setInput((previous) => ({ ...previous, ...patch }));
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy.current || conflict) return;
    const next: Record<string, string> = {};
    if (!input.atomicMetricId) next.atomicMetricId = "请选择原子指标";
    if (!input.periodId) next.periodId = "请选择时间周期";
    if (preview && !preview.fits)
      next.composition = "组合编码或名称超出长度限制，请更换更短的组成部分";
    setErrors(next);
    if (Object.keys(next).length) return;
    busy.current = true;
    setSaving(true);
    setFailure("");
    try {
      await saveDerived(props.draft, {
        ...input,
        description: input.description?.trim() || null,
      });
      if (active.current) {
        toast.success("保存成功");
        props.onSaved();
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyMetricFailure(error);
      if (result.kind === "duplicate") setErrors({ composition: "相同组成的派生指标已存在" });
      else if (result.kind === "derived")
        setErrors({ composition: "组成部分无效或组合超出长度限制" });
      else if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing") setFailure("组成部分已不存在，请刷新选项后重试。");
      else setFailure(result.kind === "unknown" ? result.message : "保存失败，请稍后重试");
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
    }
  };
  const optionsFailed =
    atomics.status === "error" || periods.status === "error" || modifiers.status === "error";
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !busy.current) props.onClose();
      }}
    >
      <DialogContent className="max-h-[86vh] w-[600px] overflow-auto rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {edited ? "编辑派生指标" : "新建派生指标"}
        </DialogTitle>
        {optionsFailed && (
          <Alert>
            组成部分选项加载失败
            <Button size="small" onClick={() => setOptionVersion((value) => value + 1)}>
              重试
            </Button>
          </Alert>
        )}
        <form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <Field className={FIELD_CLASS} invalid={Boolean(errors.atomicMetricId)}>
            <FieldLabel htmlFor={id + "-atomic"} className="pt-1" required>
              原子指标
            </FieldLabel>
            <div>
              <Select
                id={id + "-atomic"}
                value={input.atomicMetricId || null}
                size="small"
                items={(atomics.data ?? []).map((row) => ({
                  value: row.id,
                  label: row.cnName + "（" + row.enCode + "）",
                }))}
                disabled={saving || atomics.status !== "ready"}
                onValueChange={(value) => change({ atomicMetricId: value ?? "" })}
              >
                <SelectTrigger
                  id={id + "-atomic"}
                  variant="outlined"
                  aria-invalid={Boolean(errors.atomicMetricId) || undefined}
                >
                  <SelectValue placeholder="请选择" />
                </SelectTrigger>
                <SelectContent>
                  {(atomics.data ?? []).map((row) => (
                    <SelectItem key={row.id} value={row.id}>
                      <SelectItemText>{row.cnName + "（" + row.enCode + "）"}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError match={Boolean(errors.atomicMetricId)}>
                {errors.atomicMetricId}
              </FieldError>
            </div>
          </Field>
          <Field className={FIELD_CLASS} invalid={Boolean(errors.periodId)}>
            <FieldLabel htmlFor={id + "-period"} className="pt-1" required>
              时间周期
            </FieldLabel>
            <div>
              <Select
                id={id + "-period"}
                value={input.periodId || null}
                size="small"
                items={(periods.data ?? []).map((row) => ({
                  value: row.id,
                  label: row.cnName + "（" + row.enCode + "）",
                }))}
                disabled={saving || periods.status !== "ready"}
                onValueChange={(value) => change({ periodId: value ?? "" })}
              >
                <SelectTrigger
                  id={id + "-period"}
                  variant="outlined"
                  aria-invalid={Boolean(errors.periodId) || undefined}
                >
                  <SelectValue placeholder="请选择" />
                </SelectTrigger>
                <SelectContent>
                  {(periods.data ?? []).map((row) => (
                    <SelectItem key={row.id} value={row.id}>
                      <SelectItemText>{row.cnName + "（" + row.enCode + "）"}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError match={Boolean(errors.periodId)}>{errors.periodId}</FieldError>
            </div>
          </Field>
          <Field className={FIELD_CLASS}>
            <FieldLabel className="pt-1">修饰词</FieldLabel>
            <ModifierPicker
              items={(modifiers.data ?? []).map((row) => ({
                id: row.id,
                label: row.cnName + "（" + row.enCode + "）",
              }))}
              disabled={saving || modifiers.status !== "ready"}
              selected={input.modifierIds}
              onAdd={(modifierId) => change({ modifierIds: [...input.modifierIds, modifierId] })}
              onRemove={(index) =>
                change({ modifierIds: input.modifierIds.filter((_, at) => at !== index) })
              }
              onMoveUp={(index) => {
                if (index === 0) return;
                const next = [...input.modifierIds];
                [next[index - 1], next[index]] = [next[index], next[index - 1]];
                change({ modifierIds: next });
              }}
            />
          </Field>
          <Field className={FIELD_CLASS} invalid={Boolean(errors.composition) || taken}>
            <FieldLabel className="pt-1">组合预览</FieldLabel>
            <div className="space-y-1">
              {preview ? (
                <div className="space-y-1">
                  <p className="break-all font-mono text-xs">{preview.composed.enCode}</p>
                  <p className="text-xs text-[var(--yak-components-field-label)]">
                    {preview.composed.cnName}
                  </p>
                  {!preview.fits && (
                    <FieldError match>
                      组合编码或名称超出长度限制（编码最多64位、名称最多128位）
                    </FieldError>
                  )}
                  {preview.fits && taken && (
                    <FieldError match>相同组成的派生指标已存在，请调整组成部分</FieldError>
                  )}
                </div>
              ) : (
                <p className="text-xs text-[var(--yak-color-text-secondary)]">
                  选择时间周期与原子指标后生成
                </p>
              )}
              <FieldError match={Boolean(errors.composition)}>{errors.composition}</FieldError>
            </div>
          </Field>
          <Field className={FIELD_CLASS} invalid={Boolean(errors.description)}>
            <FieldLabel htmlFor={id + "-description"} className="pt-1">
              说明
            </FieldLabel>
            <div>
              <Textarea
                id={id + "-description"}
                size="small"
                variant="outlined"
                value={input.description ?? ""}
                rows={2}
                maxLength={500}
                disabled={saving}
                onValueChange={(value) => change({ description: String(value ?? "") || null })}
              />
              <FieldError match={Boolean(errors.description)}>{errors.description}</FieldError>
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
              disabled={conflict || taken || (preview !== null && !preview.fits)}
            >
              保存
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

interface Props {
  unavailable: boolean;
  version: number;
  onChanged: () => void;
  onDelete: (target: MetricDeleteTarget) => void;
}

export default function DerivedSection(props: Props) {
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [draft, setDraft] = useState<
    { mode: "create" } | { mode: "edit"; node: MetricDerived } | null
  >(null);
  const key = props.unavailable ? null : [keyword, pageNo, pageSize].join(":");
  const list = useMetricPage(key, props.version, (signal) =>
    getDerived({ pageNo, pageSize, keyword }, signal),
  );
  useClampPage(list.page, list.loading, pageNo, setPageNo);
  const columns: TableColumns<MetricDerived> = [
    { title: "组合编码", dataIndex: "enCode", width: 220, ellipsis: true },
    { title: "组合名称", dataIndex: "cnName", width: 200, ellipsis: true },
    { title: "原子指标", dataIndex: "atomicEnCode", width: 140, ellipsis: true },
    { title: "时间周期", dataIndex: "periodEnCode", width: 110 },
    {
      title: "修饰词",
      key: "modifiers",
      width: 180,
      ellipsis: true,
      render: (_, row) => row.modifiers.map((part) => part.modifierEnCode).join(" / "),
    },
    { title: "更新时间", dataIndex: "updateTime", width: 180 },
    {
      title: "操作",
      key: "actions",
      width: 120,
      fixed: "right",
      align: "center",
      render: (_, row) => (
        <div className="flex items-center justify-center gap-2">
          <Button
            size="small"
            variant="ghost"
            disabled={props.unavailable}
            className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
            onClick={() => setDraft({ mode: "edit", node: row })}
          >
            编辑
          </Button>
          <Button
            size="small"
            variant="ghost"
            disabled={props.unavailable}
            className="px-0.5 font-normal text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-components-danger-text)]"
            onClick={() => props.onDelete({ kind: "derived", node: row })}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];
  return (
    <>
      <MetricToolbar
        disabled={props.unavailable}
        searching={Boolean(keyword)}
        searchText={searchText}
        onSearchTextChange={setSearchText}
        onSearch={() => {
          setKeyword(searchText.trim());
          setPageNo(1);
        }}
        onClear={() => {
          setSearchText("");
          setKeyword("");
          setPageNo(1);
        }}
        createLabel="新建派生指标"
        onCreate={() => setDraft({ mode: "create" })}
      />
      {list.failed && (
        <Alert>
          派生指标加载失败
          <Button size="small" onClick={props.onChanged}>
            重试
          </Button>
        </Alert>
      )}
      <div className="min-h-0 min-w-0 flex-1 overflow-auto">
        <Table<MetricDerived>
          rowKey="id"
          columns={columns}
          dataSource={list.page?.bizData ?? []}
          loading={props.unavailable || list.loading}
          size="small"
          bordered={false}
          scroll={{ x: 1150 }}
          emptyText="暂无派生指标"
          pagination={{
            current: pageNo,
            pageSize,
            total: list.page?.pagination.total ?? 0,
            disabled: list.loading,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100],
            onChange: (page, size) => {
              setPageSize(size);
              setPageNo(size === pageSize ? page : 1);
            },
          }}
        />
      </div>
      {draft && (
        <DerivedDialog
          draft={draft}
          onClose={() => setDraft(null)}
          onSaved={() => {
            setDraft(null);
            props.onChanged();
          }}
        />
      )}
    </>
  );
}
