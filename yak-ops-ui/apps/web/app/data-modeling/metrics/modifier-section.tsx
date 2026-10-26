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
  classifyMetricFailure,
  getModifiers,
  saveModifier,
  checkModifierCode,
} from "@/service/data-modeling/metrics";
import { getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import type {
  MetricDeleteTarget,
  MetricModifier,
  MetricModifierInput,
} from "@/service/data-modeling/metrics/types";
import { METRIC_CODE_PATTERN } from "@/service/data-modeling/metrics/types";
import { FIELD_CLASS, InputField, SelectField } from "../shared/form-fields";
import MetricToolbar from "./metric-toolbar";
import { domainLabel, domainsOf } from "./planning-options";
import { useClampPage, useMetricOptions, useMetricPage, useRetry } from "./page-state";

function validate(input: MetricModifierInput) {
  const errors: Record<string, string> = {};
  if (!input.domainId) errors.domainId = "请选择所属数据域";
  if (!METRIC_CODE_PATTERN.test(input.enCode))
    errors.enCode = "须以小写字母开头，仅含小写字母、数字或下划线，最多64位";
  if (!input.cnName.trim() || input.cnName.trim().length > 128)
    errors.cnName = "请输入1至128个字符的中文名称";
  if ((input.description?.length ?? 0) > 500) errors.description = "说明不能超过500个字符";
  return errors;
}

function ModifierDialog(props: {
  draft: { mode: "create" } | { mode: "edit"; node: MetricModifier };
  onClose: () => void;
  onSaved: () => void;
}) {
  const edited = props.draft.mode === "edit" ? props.draft.node : undefined;
  const [input, setInput] = useState<MetricModifierInput>(() => ({
    domainId: edited?.domainId ?? "",
    enCode: edited?.enCode ?? "",
    cnName: edited?.cnName ?? "",
    description: edited?.description ?? null,
  }));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const [retry, refreshOptions] = useRetry();
  const options = useMetricOptions(
    (signal) => getPlanningTree(signal).then((tree) => domainsOf(tree)),
    retry,
  );
  const active = useRef(true),
    busy = useRef(false);
  const id = useId();
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const change = (patch: Partial<MetricModifierInput>) =>
    setInput((previous) => ({ ...previous, ...patch }));
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy.current || conflict) return;
    const next = validate(input);
    setErrors(next);
    if (Object.keys(next).length) return;
    busy.current = true;
    setSaving(true);
    setFailure("");
    if (!edited) {
      try {
        const available = await checkModifierCode(input.enCode);
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
      await saveModifier(props.draft, {
        ...input,
        cnName: input.cnName.trim(),
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
      else if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing")
        setFailure(
          input.domainId && !edited
            ? "所属数据域已不存在，请重新选择。"
            : "该修饰词已不存在，请刷新后重试。",
        );
      else setFailure(result.kind === "unknown" ? result.message : "保存失败，请稍后重试");
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
    }
  };
  const domainItems = (options.data ?? []).map((choice) => ({
    value: choice.id,
    label: domainLabel(choice),
  }));
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !busy.current) props.onClose();
      }}
    >
      <DialogContent className="max-h-[86vh] w-[560px] overflow-auto rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {edited ? "编辑修饰词" : "新建修饰词"}
        </DialogTitle>
        {options.status === "error" && (
          <Alert>
            数据域加载失败
            <Button size="small" onClick={refreshOptions}>
              重试
            </Button>
          </Alert>
        )}
        <Form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <SelectField
            id={id + "-domain"}
            label="所属数据域"
            value={input.domainId}
            items={domainItems}
            disabled={saving || options.status !== "ready"}
            error={errors.domainId}
            onChange={(value) => change({ domainId: value })}
          />
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
            hint="将作为派生指标编码的中间段，按提交顺序拼接"
            onChange={(value) => change({ enCode: value })}
          />
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

export default function ModifierSection(props: Props) {
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [draft, setDraft] = useState<
    { mode: "create" } | { mode: "edit"; node: MetricModifier } | null
  >(null);
  const key = props.unavailable ? null : [keyword, pageNo, pageSize].join(":");
  const list = useMetricPage(key, props.version, (signal) =>
    getModifiers({ pageNo, pageSize, keyword }, signal),
  );
  useClampPage(list.page, list.loading, pageNo, setPageNo);
  const columns: TableColumns<MetricModifier> = [
    { title: "中文名称", dataIndex: "cnName", width: 160, ellipsis: true },
    { title: "英文编码", dataIndex: "enCode", width: 160, ellipsis: true },
    { title: "所属数据域", dataIndex: "domainName", width: 180, ellipsis: true },
    { title: "说明", dataIndex: "description", width: 220, ellipsis: true },
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
            className="px-0.5 font-normal text-[#667085] hover:text-[#d92d20]"
            onClick={() => props.onDelete({ kind: "modifier", node: row })}
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
        createLabel="新建修饰词"
        onCreate={() => setDraft({ mode: "create" })}
      />
      {list.failed && (
        <Alert>
          修饰词加载失败
          <Button size="small" onClick={props.onChanged}>
            重试
          </Button>
        </Alert>
      )}
      <div className="min-h-0 min-w-0 flex-1 overflow-auto">
        <Table<MetricModifier>
          rowKey="id"
          columns={columns}
          dataSource={list.page?.bizData ?? []}
          loading={props.unavailable || list.loading}
          size="small"
          bordered={false}
          scroll={{ x: 1020 }}
          emptyText="暂无修饰词"
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
        <ModifierDialog
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
