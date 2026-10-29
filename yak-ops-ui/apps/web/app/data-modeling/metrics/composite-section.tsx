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
  Table,
  Textarea,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import {
  checkCompositeCode,
  classifyMetricFailure,
  getComposites,
  saveComposite,
} from "@/service/data-modeling/metrics";
import type {
  MetricComposite,
  MetricCompositeInput,
  MetricDeleteTarget,
} from "@/service/data-modeling/metrics/types";
import { METRIC_CODE_PATTERN } from "@/service/data-modeling/metrics/types";
import { FIELD_CLASS, InputField } from "../shared/form-fields";
import { metricExpressionReferences } from "./expression";
import MetricToolbar from "./metric-toolbar";
import { useClampPage, useMetricPage } from "./page-state";

function validate(input: MetricCompositeInput, references: string[] | null) {
  const errors: Record<string, string> = {};
  if (!METRIC_CODE_PATTERN.test(input.enCode))
    errors.enCode = "须以小写字母开头，仅含小写字母、数字或下划线，最多64位";
  if (!input.cnName.trim() || input.cnName.trim().length > 128)
    errors.cnName = "请输入1至128个字符的中文名称";
  if (!input.expression.trim() || references === null)
    errors.expression = "表达式只能包含 +、-、*、/、括号、数字与派生指标编码";
  else if (references.length === 0) errors.expression = "表达式至少引用一个派生指标";
  if ((input.description?.length ?? 0) > 500) errors.description = "说明不能超过500个字符";
  return errors;
}

function CompositeDialog(props: {
  draft: { mode: "create" } | { mode: "edit"; node: MetricComposite };
  onClose: () => void;
  onSaved: () => void;
}) {
  const edited = props.draft.mode === "edit" ? props.draft.node : undefined;
  const [input, setInput] = useState<MetricCompositeInput>(() => ({
    enCode: edited?.enCode ?? "",
    cnName: edited?.cnName ?? "",
    expression: edited?.expression ?? "",
    ownerId: edited?.ownerId ?? null,
    description: edited?.description ?? null,
  }));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const active = useRef(true),
    busy = useRef(false);
  const id = useId();
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const references = metricExpressionReferences(input.expression);
  const change = (patch: Partial<MetricCompositeInput>) =>
    setInput((previous) => ({ ...previous, ...patch }));
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy.current || conflict) return;
    const next = validate(input, references);
    setErrors(next);
    if (Object.keys(next).length) return;
    busy.current = true;
    setSaving(true);
    setFailure("");
    if (!edited) {
      try {
        const available = await checkCompositeCode(input.enCode);
        if (!available.available) {
          if (active.current) {
            setErrors({ enCode: "英文编码已存在" });
            setSaving(false);
          }
          busy.current = false;
          return;
        }
      } catch {
        // Availability is an optimization; the server still answers authoritatively.
      }
    }
    try {
      await saveComposite(props.draft, {
        ...input,
        cnName: input.cnName.trim(),
        ownerId: input.ownerId || null,
        description: input.description?.trim() || null,
      });
      if (active.current) {
        toast.success("保存成功");
        props.onSaved();
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyMetricFailure(error);
      if (result.kind === "field" && result.field === "enCode")
        setErrors({ enCode: "英文编码已存在或不符合规范" });
      else if (result.kind === "duplicate") setErrors({ enCode: "英文编码已存在" });
      else if (result.kind === "expression")
        setErrors({ expression: result.message || "表达式无效或引用了不存在的派生指标" });
      else if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing") setFailure("该复合指标已不存在，请刷新后重试。");
      else setFailure(result.kind === "unknown" ? result.message : "保存失败，请稍后重试");
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
        if (!open && !busy.current) props.onClose();
      }}
    >
      <DialogContent className="max-h-[86vh] w-[600px] overflow-auto rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {edited ? "编辑复合指标" : "新建复合指标"}
        </DialogTitle>
        <Form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <InputField
            id={id + "-name"}
            label="中文名称"
            value={input.cnName}
            required
            maxLength={128}
            disabled={saving}
            error={errors.cnName}
            onChange={(value) => change({ cnName: value })}
          />
          <InputField
            id={id + "-code"}
            label="英文编码"
            value={input.enCode}
            required
            maxLength={64}
            disabled={saving}
            error={errors.enCode}
            onChange={(value) => change({ enCode: value })}
          />
          <Field className={FIELD_CLASS} invalid={Boolean(errors.expression)}>
            <FieldLabel htmlFor={id + "-expression"} className="pt-1" required>
              计算表达式
            </FieldLabel>
            <div>
              <Textarea
                id={id + "-expression"}
                size="small"
                variant="outlined"
                value={input.expression}
                rows={3}
                maxLength={2048}
                disabled={saving}
                placeholder="如：(d7_pay_amt - d1_pay_amt) / d1_pay_amt * 100"
                onValueChange={(value) => change({ expression: String(value ?? "") })}
              />
              <FieldError match={Boolean(errors.expression)}>{errors.expression}</FieldError>
              <p className="mt-1 text-xs leading-4 text-[var(--yak-color-text-secondary)]">
                支持四则运算、括号与数字；引用的派生指标编码是否存在由服务端保存时校验。
              </p>
            </div>
          </Field>
          {references !== null && (
            <Field className={FIELD_CLASS}>
              <FieldLabel className="pt-1">引用解析</FieldLabel>
              <div className="flex flex-wrap gap-1">
                {references.length === 0 && (
                  <p className="text-xs text-[var(--yak-color-text-secondary)]">
                    未引用任何派生指标
                  </p>
                )}
                {references.map((code) => (
                  <span
                    key={code}
                    className="rounded bg-[var(--yak-components-badge-neutral-bg)] px-1.5 py-0.5 font-mono text-xs text-[var(--yak-components-field-label)]"
                  >
                    {code}
                  </span>
                ))}
              </div>
            </Field>
          )}
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
              disabled={conflict}
            >
              保存
            </Button>
          </div>
        </Form>
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

export default function CompositeSection(props: Props) {
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [draft, setDraft] = useState<
    { mode: "create" } | { mode: "edit"; node: MetricComposite } | null
  >(null);
  const key = props.unavailable ? null : [keyword, pageNo, pageSize].join(":");
  const list = useMetricPage(key, props.version, (signal) =>
    getComposites({ pageNo, pageSize, keyword }, signal),
  );
  useClampPage(list.page, list.loading, pageNo, setPageNo);
  const columns: TableColumns<MetricComposite> = [
    { title: "中文名称", dataIndex: "cnName", width: 160, ellipsis: true },
    { title: "英文编码", dataIndex: "enCode", width: 160, ellipsis: true },
    {
      title: "计算表达式",
      dataIndex: "expression",
      width: 320,
      ellipsis: true,
      render: (_, row) => <span className="font-mono text-xs">{row.expression}</span>,
    },
    {
      title: "引用",
      key: "references",
      width: 180,
      ellipsis: true,
      render: (_, row) => row.references.map((ref) => ref.derivedEnCode).join(" / "),
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
            onClick={() => props.onDelete({ kind: "composite", node: row })}
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
        createLabel="新建复合指标"
        onCreate={() => setDraft({ mode: "create" })}
      />
      {list.failed && (
        <Alert>
          复合指标加载失败
          <Button size="small" onClick={props.onChanged}>
            重试
          </Button>
        </Alert>
      )}
      <div className="min-h-0 min-w-0 flex-1 overflow-auto">
        <Table<MetricComposite>
          rowKey="id"
          columns={columns}
          dataSource={list.page?.bizData ?? []}
          loading={props.unavailable || list.loading}
          size="small"
          bordered={false}
          scroll={{ x: 1120 }}
          emptyText="暂无复合指标"
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
        <CompositeDialog
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
