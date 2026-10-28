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
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import {
  classifyStandardFailure,
  getMeasureUnits,
  saveMeasureUnit,
} from "@/service/data-modeling/data-standards";
import type {
  DeleteTarget,
  MeasureUnitCategory,
  StandardMeasureUnit,
  StandardMeasureUnitInput,
} from "@/service/data-modeling/data-standards/types";
import {
  MEASURE_UNIT_CATEGORIES,
  MEASURE_UNIT_LABEL,
} from "@/service/data-modeling/data-standards/types";
import { FIELD_CLASS, InputField, SelectField } from "../shared/form-fields";
import KindToolbar from "./kind-toolbar";
import { useKindPage } from "./state";

const CODE = /^[a-z][a-z0-9_]{0,63}$/;
const ALL = "ALL";

function validate(input: StandardMeasureUnitInput) {
  const errors: Record<string, string> = {};
  if (!CODE.test(input.enCode))
    errors.enCode = "须以小写字母开头，仅含小写字母、数字或下划线，最多64位";
  if (!input.cnName.trim() || input.cnName.trim().length > 128)
    errors.cnName = "请输入1至128个字符的中文名称";
  if (!input.enName.trim() || input.enName.trim().length > 128)
    errors.enName = "请输入1至128个字符的英文名称";
  if ((input.description?.length ?? 0) > 500) errors.description = "说明不能超过500个字符";
  return errors;
}

function UnitDialog(props: {
  draft: { mode: "create" } | { mode: "edit"; unit: StandardMeasureUnit };
  onClose: () => void;
  onSaved: () => void;
}) {
  const edited = props.draft.mode === "edit" ? props.draft.unit : undefined;
  const [input, setInput] = useState<StandardMeasureUnitInput>(() => ({
    category: edited?.category ?? "CURRENCY",
    enCode: edited?.enCode ?? "",
    enName: edited?.enName ?? "",
    cnName: edited?.cnName ?? "",
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
  const change = (patch: Partial<StandardMeasureUnitInput>) =>
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
    try {
      await saveMeasureUnit(props.draft, {
        ...input,
        enName: input.enName.trim(),
        cnName: input.cnName.trim(),
        description: input.description?.trim() || null,
      });
      if (active.current) {
        toast.success("保存成功");
        props.onSaved();
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyStandardFailure(error);
      if (result.kind === "field" && result.field === "enCode")
        setErrors({ enCode: "英文编码已存在或不符合规范" });
      else if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing") setFailure("该度量单位已不存在，请刷新后重试。");
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
      <DialogContent className="max-h-[86vh] w-[560px] overflow-auto rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {edited ? "编辑度量单位" : "新建度量单位"}
        </DialogTitle>
        <Form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <SelectField
            id={id + "-category"}
            label="单位类型"
            value={input.category}
            disabled={saving}
            items={MEASURE_UNIT_CATEGORIES.map((value) => ({
              value,
              label: MEASURE_UNIT_LABEL[value],
            }))}
            onChange={(value) => {
              const next = MEASURE_UNIT_CATEGORIES.find((item) => item === value);
              if (next) change({ category: next });
            }}
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
            onChange={(value) => change({ enCode: value })}
          />
          <InputField
            id={id + "-en-name"}
            label="英文名称"
            value={input.enName}
            required
            maxLength={128}
            disabled={saving}
            error={errors.enName}
            onChange={(value) => change({ enName: value })}
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
  onDelete: (target: DeleteTarget) => void;
}

export default function UnitSection(props: Props) {
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState<MeasureUnitCategory | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [draft, setDraft] = useState<
    { mode: "create" } | { mode: "edit"; unit: StandardMeasureUnit } | null
  >(null);
  const key = props.unavailable ? null : [category ?? "", keyword, pageNo, pageSize].join(":");
  const list = useKindPage(key, props.version, (signal) =>
    getMeasureUnits(category, keyword, pageNo, pageSize, signal),
  );
  const columns: TableColumns<StandardMeasureUnit> = [
    { title: "中文名称", dataIndex: "cnName", width: 180, ellipsis: true },
    { title: "英文编码", dataIndex: "enCode", width: 160, ellipsis: true },
    { title: "英文名称", dataIndex: "enName", width: 180, ellipsis: true },
    {
      title: "单位类型",
      key: "category",
      width: 110,
      render: (_, row) => MEASURE_UNIT_LABEL[row.category],
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
            onClick={() => setDraft({ mode: "edit", unit: row })}
          >
            编辑
          </Button>
          <Button
            size="small"
            variant="ghost"
            disabled={props.unavailable}
            className="px-0.5 font-normal text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-components-danger-text)]"
            onClick={() => props.onDelete({ kind: "unit", node: row })}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];
  return (
    <>
      <KindToolbar
        disabled={props.unavailable}
        searching={Boolean(keyword) || category !== null}
        searchText={searchText}
        onSearchTextChange={setSearchText}
        onSearch={() => {
          setKeyword(searchText.trim());
          setPageNo(1);
        }}
        onClear={() => {
          setSearchText("");
          setKeyword("");
          setCategory(null);
          setPageNo(1);
        }}
        createLabel="新建度量单位"
        createDisabled={false}
        hint={
          <Select
            value={category ?? ALL}
            size="small"
            items={[
              { value: ALL, label: "全部类型" },
              ...MEASURE_UNIT_CATEGORIES.map((value) => ({
                value,
                label: MEASURE_UNIT_LABEL[value],
              })),
            ]}
            disabled={props.unavailable}
            onValueChange={(value) => {
              const next = MEASURE_UNIT_CATEGORIES.find((item) => item === value);
              setCategory(next ?? null);
              setPageNo(1);
            }}
          >
            <SelectTrigger variant="outlined" aria-label="按单位类型筛选">
              <SelectValue placeholder="全部类型" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>
                <SelectItemText>全部类型</SelectItemText>
              </SelectItem>
              {MEASURE_UNIT_CATEGORIES.map((value) => (
                <SelectItem key={value} value={value}>
                  <SelectItemText>{MEASURE_UNIT_LABEL[value]}</SelectItemText>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        }
        onCreate={() => setDraft({ mode: "create" })}
      />
      {list.failed && (
        <Alert>
          度量单位加载失败
          <Button size="small" onClick={props.onChanged}>
            重试
          </Button>
        </Alert>
      )}
      <div className="min-h-0 min-w-0 flex-1 overflow-auto">
        <Table<StandardMeasureUnit>
          rowKey="id"
          columns={columns}
          dataSource={list.page?.bizData ?? []}
          loading={props.unavailable || list.loading}
          size="small"
          bordered={false}
          scroll={{ x: 940 }}
          emptyText="暂无度量单位"
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
        <UnitDialog
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
