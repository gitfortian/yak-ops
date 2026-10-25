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
import { getMeasureUnits, getOwners } from "@/service/data-modeling/data-standards";
import {
  checkAtomicCode,
  classifyMetricFailure,
  getAtomics,
  saveAtomic,
} from "@/service/data-modeling/metrics";
import { getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import type {
  AggregateType,
  DataType,
  MetricAtomic,
  MetricAtomicInput,
  MetricDeleteTarget,
} from "@/service/data-modeling/metrics/types";
import {
  AGGREGATE_LABEL,
  AGGREGATE_TYPES,
  DATA_TYPES,
  METRIC_CODE_PATTERN,
} from "@/service/data-modeling/metrics/types";
import { FIELD_CLASS, InputField, SelectField } from "../shared/form-fields";
import MetricToolbar from "./metric-toolbar";
import { processesOf } from "./planning-options";
import { useMetricOptions, useMetricPage, useRetry } from "./page-state";

/** Mirrors the server's AggregateType.supports: SUM and AVG need arithmetic types. */
function aggregateSupports(aggregateType: AggregateType, dataType: DataType): boolean {
  return aggregateType === "SUM" || aggregateType === "AVG"
    ? dataType === "BIGINT" || dataType === "DECIMAL"
    : true;
}

function validate(input: MetricAtomicInput) {
  const errors: Record<string, string> = {};
  if (!input.processId) errors.processId = "请选择所属业务过程";
  if (!METRIC_CODE_PATTERN.test(input.enCode))
    errors.enCode = "须以小写字母开头，仅含小写字母、数字或下划线，最多64位";
  if (!input.cnName.trim() || input.cnName.trim().length > 128)
    errors.cnName = "请输入1至128个字符的中文名称";
  if (!aggregateSupports(input.aggregateType, input.dataType))
    errors.aggregateType = "求和与平均仅支持 BIGINT、DECIMAL";
  if ((input.description?.length ?? 0) > 500) errors.description = "说明不能超过500个字符";
  return errors;
}

function AtomicDialog(props: {
  draft: { mode: "create" } | { mode: "edit"; node: MetricAtomic };
  onClose: () => void;
  onSaved: () => void;
}) {
  const edited = props.draft.mode === "edit" ? props.draft.node : undefined;
  const [input, setInput] = useState<MetricAtomicInput>(() => ({
    processId: edited?.processId ?? "",
    enCode: edited?.enCode ?? "",
    cnName: edited?.cnName ?? "",
    dataType: edited?.dataType ?? "DECIMAL",
    aggregateType: edited?.aggregateType ?? "SUM",
    measureUnitId: edited?.measureUnitId ?? null,
    ownerId: null,
    description: edited?.description ?? null,
  }));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const [retry, refreshOptions] = useRetry();
  const processes = useMetricOptions(
    (signal) => getPlanningTree(signal).then((tree) => processesOf(tree)),
    retry,
  );
  const units = useMetricOptions(
    (signal) => getMeasureUnits(null, "", 1, 100, signal).then((page) => page.bizData),
    retry,
  );
  const owners = useMetricOptions((signal) => getOwners(signal), retry);
  const active = useRef(true),
    busy = useRef(false);
  const id = useId();
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const change = (patch: Partial<MetricAtomicInput>) =>
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
        const available = await checkAtomicCode(input.enCode);
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
      await saveAtomic(props.draft, {
        ...input,
        cnName: input.cnName.trim(),
        measureUnitId: input.measureUnitId || null,
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
      else if (result.kind === "field" && result.field === "ownerId")
        setErrors({ ownerId: "请选择当前工作空间的有效成员" });
      else if (result.kind === "atomic") setErrors({ aggregateType: "聚合方式与数据类型不匹配" });
      else if (result.kind === "duplicate") setErrors({ enCode: "英文编码已存在" });
      else if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing")
        setFailure("所属业务过程或度量单位已不存在，请重新选择。");
      else setFailure(result.kind === "unknown" ? result.message : "保存失败，请稍后重试");
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
    }
  };
  const processItems = (processes.data ?? []).map((choice) => ({
    value: choice.id,
    label: choice.domainName + " / " + choice.name,
  }));
  const unitItems = [
    { value: "", label: "不绑定" },
    ...(units.data ?? []).map((unit) => ({
      value: unit.id,
      label: unit.cnName + "（" + unit.enCode + "）",
    })),
  ];
  const ownerItems = [
    { value: "", label: "默认当前用户" },
    ...(owners.data ?? []).map((owner) => ({ value: owner.userId, label: owner.displayName })),
  ];
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
          {edited ? "编辑原子指标" : "新建原子指标"}
        </DialogTitle>
        {processes.status === "error" && (
          <Alert>
            业务过程加载失败
            <Button size="small" onClick={refreshOptions}>
              重试
            </Button>
          </Alert>
        )}
        <Form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <SelectField
            id={id + "-process"}
            label="所属业务过程"
            value={input.processId}
            items={processItems}
            disabled={saving || processes.status !== "ready"}
            error={errors.processId}
            onChange={(value) => change({ processId: value })}
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
            hint="将作为派生指标编码的最后一段"
            onChange={(value) => change({ enCode: value })}
          />
          <SelectField
            id={id + "-data-type"}
            label="数据类型"
            value={input.dataType}
            items={DATA_TYPES.map((value) => ({ value, label: value }))}
            disabled={saving}
            onChange={(value) => {
              const next = DATA_TYPES.find((item) => item === value);
              if (next) change({ dataType: next });
            }}
          />
          <SelectField
            id={id + "-aggregate"}
            label="聚合方式"
            value={input.aggregateType}
            items={AGGREGATE_TYPES.map((value) => ({ value, label: AGGREGATE_LABEL[value] }))}
            disabled={saving}
            error={errors.aggregateType}
            onChange={(value) => {
              const next = AGGREGATE_TYPES.find((item) => item === value);
              if (next) change({ aggregateType: next });
            }}
          />
          <SelectField
            id={id + "-unit"}
            label="度量单位"
            value={input.measureUnitId ?? ""}
            items={unitItems}
            required={false}
            disabled={saving || units.status !== "ready"}
            onChange={(value) => change({ measureUnitId: value || null })}
          />
          <SelectField
            id={id + "-owner"}
            label="负责人"
            value={input.ownerId ?? ""}
            items={ownerItems}
            required={false}
            disabled={saving || owners.status !== "ready"}
            error={errors.ownerId}
            onChange={(value) => change({ ownerId: value || null })}
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

export default function AtomicSection(props: Props) {
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [draft, setDraft] = useState<
    { mode: "create" } | { mode: "edit"; node: MetricAtomic } | null
  >(null);
  const key = props.unavailable ? null : [keyword, pageNo, pageSize].join(":");
  const list = useMetricPage(key, props.version, (signal) =>
    getAtomics({ pageNo, pageSize, keyword }, signal),
  );
  const columns: TableColumns<MetricAtomic> = [
    { title: "中文名称", dataIndex: "cnName", width: 160, ellipsis: true },
    { title: "英文编码", dataIndex: "enCode", width: 160, ellipsis: true },
    { title: "所属业务过程", dataIndex: "processName", width: 160, ellipsis: true },
    { title: "数据类型", dataIndex: "dataType", width: 100 },
    {
      title: "聚合方式",
      key: "aggregateType",
      width: 100,
      render: (_, row) => AGGREGATE_LABEL[row.aggregateType],
    },
    { title: "度量单位", dataIndex: "measureUnitEnCode", width: 110, ellipsis: true },
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
            onClick={() => props.onDelete({ kind: "atomic", node: row })}
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
        createLabel="新建原子指标"
        onCreate={() => setDraft({ mode: "create" })}
      />
      {list.failed && (
        <Alert>
          原子指标加载失败
          <Button size="small" onClick={props.onChanged}>
            重试
          </Button>
        </Alert>
      )}
      <div className="min-h-0 min-w-0 flex-1 overflow-auto">
        <Table<MetricAtomic>
          rowKey="id"
          columns={columns}
          dataSource={list.page?.bizData ?? []}
          loading={props.unavailable || list.loading}
          size="small"
          bordered={false}
          scroll={{ x: 1180 }}
          emptyText="暂无原子指标"
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
        <AtomicDialog
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
