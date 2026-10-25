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
  Table,
  Textarea,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Plus, Trash2 } from "lucide-react";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import {
  classifyStandardFailure,
  getLookup,
  getLookups,
  saveLookup,
} from "@/service/data-modeling/data-standards";
import type {
  DeleteTarget,
  StandardLookup,
  StandardLookupInput,
  StandardLookupValueInput,
} from "@/service/data-modeling/data-standards/types";
import { FIELD_CLASS, InputField } from "../shared/form-fields";
import KindToolbar from "./kind-toolbar";
import { useKindPage } from "./state";

const LOOKUP_CODE = /^[a-z][a-z0-9_]{0,63}$/;
const MAX_VALUE_CODE = 64;
/** Mirrors the service-side cap on one submitted value list. */
const MAX_VALUES = 1000;

interface Draft {
  mode: "create";
  categoryId: string;
  path: string;
}
interface EditDraft {
  mode: "edit";
  lookup: StandardLookup;
}
type LookupDraft = Draft | EditDraft;

function validate(input: StandardLookupInput) {
  const errors: Record<string, string> = {};
  if (!LOOKUP_CODE.test(input.enCode))
    errors.enCode = "须以小写字母开头，仅含小写字母、数字或下划线，最多64位";
  if (!input.cnName.trim() || input.cnName.trim().length > 128)
    errors.cnName = "请输入1至128个字符的中文名称";
  if (!input.enName.trim() || input.enName.trim().length > 128)
    errors.enName = "请输入1至128个字符的英文名称";
  if ((input.description?.length ?? 0) > 500) errors.description = "说明不能超过500个字符";
  const seen = new Set<string>();
  input.values.forEach((value, index) => {
    const code = value.valueCode.trim();
    if (!code || code.length > MAX_VALUE_CODE) {
      errors["value-" + index] = "值编码必填，且不超过64位";
      return;
    }
    if (seen.has(code)) errors["value-" + index] = "同一码表内的值编码不能重复";
    seen.add(code);
  });
  return errors;
}

function LookupDialog(props: { draft: LookupDraft; onClose: () => void; onSaved: () => void }) {
  const [input, setInput] = useState<StandardLookupInput>(() => ({
    enCode: props.draft.mode === "edit" ? props.draft.lookup.enCode : "",
    cnName: props.draft.mode === "edit" ? props.draft.lookup.cnName : "",
    enName: props.draft.mode === "edit" ? props.draft.lookup.enName : "",
    description: props.draft.mode === "edit" ? props.draft.lookup.description : null,
    values:
      props.draft.mode === "edit"
        ? (props.draft.lookup.values ?? []).map((value) => ({
            valueCode: value.valueCode,
            cnName: value.cnName,
            enName: value.enName,
            meaning: value.meaning,
          }))
        : [{ valueCode: "", cnName: null, enName: null, meaning: null }],
  }));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(props.draft.mode === "edit");
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
  useEffect(() => {
    if (props.draft.mode !== "edit") return;
    const controller = new AbortController();
    let current = true;
    setLoading(true);
    void getLookup(props.draft.lookup.id, controller.signal)
      .then((row) => {
        if (!current) return;
        setInput({
          enCode: row.enCode,
          cnName: row.cnName,
          enName: row.enName,
          description: row.description,
          values: (row.values ?? []).map((value) => ({
            valueCode: value.valueCode,
            cnName: value.cnName,
            enName: value.enName,
            meaning: value.meaning,
          })),
        });
      })
      .catch(() => {
        if (current) setFailure("码表加载失败，请关闭弹窗后重试。");
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [props.draft]);
  const value = (index: number, patch: Partial<StandardLookupValueInput>) =>
    setInput((previous) => ({
      ...previous,
      values: previous.values.map((row, position) =>
        position === index ? { ...row, ...patch } : row,
      ),
    }));
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy.current || conflict || loading) return;
    const next = validate(input);
    setErrors(next);
    if (Object.keys(next).length) return;
    busy.current = true;
    setSaving(true);
    setFailure("");
    try {
      await saveLookup(
        props.draft.mode === "edit"
          ? { mode: "edit", lookup: props.draft.lookup }
          : { mode: "create", categoryId: props.draft.categoryId },
        {
          ...input,
          cnName: input.cnName.trim(),
          enName: input.enName.trim(),
          description: input.description?.trim() || null,
          values: input.values.map((row) => ({
            valueCode: row.valueCode.trim(),
            cnName: row.cnName?.trim() || null,
            enName: row.enName?.trim() || null,
            meaning: row.meaning?.trim() || null,
          })),
        },
      );
      if (active.current) {
        toast.success("保存成功");
        props.onSaved();
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyStandardFailure(error);
      if (result.kind === "field" && result.field === "enCode")
        setErrors({ enCode: "英文编码已存在或不符合规范" });
      else if (result.kind === "values") setFailure("码表值不合法：编码不能为空且不能重复。");
      else if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing") setFailure("所属分类或码表已不存在，请刷新后重试。");
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
      <DialogContent className="max-h-[86vh] w-[720px] overflow-auto rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {props.draft.mode === "edit" ? "编辑标准代码" : "新建标准代码"}
        </DialogTitle>
        <Form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <InputField
            id={id + "-category"}
            label="所属分类"
            value={
              props.draft.mode === "create" ? props.draft.path : props.draft.lookup.categoryPath
            }
            required
            disabled
          />
          <InputField
            id={id + "-name"}
            label="中文名称"
            value={input.cnName}
            required
            maxLength={128}
            disabled={saving || loading}
            error={errors.cnName}
            onChange={(next) => {
              setInput((previous) => ({ ...previous, cnName: next }));
              setErrors((previous) => ({ ...previous, cnName: "" }));
            }}
          />
          <InputField
            id={id + "-code"}
            label="英文编码"
            value={input.enCode}
            required
            maxLength={64}
            disabled={saving || loading}
            error={errors.enCode}
            onChange={(next) => {
              setInput((previous) => ({ ...previous, enCode: next }));
              setErrors((previous) => ({ ...previous, enCode: "" }));
            }}
          />
          <InputField
            id={id + "-en-name"}
            label="英文名称"
            value={input.enName}
            required
            maxLength={128}
            disabled={saving || loading}
            error={errors.enName}
            onChange={(next) => {
              setInput((previous) => ({ ...previous, enName: next }));
              setErrors((previous) => ({ ...previous, enName: "" }));
            }}
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
                disabled={saving || loading}
                onValueChange={(next) =>
                  setInput((previous) => ({
                    ...previous,
                    description: String(next ?? "") || null,
                  }))
                }
              />
              <FieldError match={Boolean(errors.description)}>{errors.description}</FieldError>
            </div>
          </Field>
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium">码表值</span>
              <Button
                size="small"
                disabled={saving || loading || input.values.length >= MAX_VALUES}
                onClick={() =>
                  setInput((previous) => ({
                    ...previous,
                    values: [
                      ...previous.values,
                      { valueCode: "", cnName: null, enName: null, meaning: null },
                    ],
                  }))
                }
              >
                <Plus size={14} />
                添加值
              </Button>
            </div>
            {input.values.length >= MAX_VALUES && (
              <p className="text-xs text-[#b54708]">一张码表最多 {MAX_VALUES} 个值。</p>
            )}
            {input.values.length === 0 && (
              <p className="text-xs text-[#667085]">码表至少需要一个值。</p>
            )}
            {input.values.map((row, index) => (
              <div key={index}>
                <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.4fr)_auto] items-center gap-2">
                  <Input
                    size="small"
                    variant="outlined"
                    value={row.valueCode}
                    maxLength={MAX_VALUE_CODE}
                    disabled={saving || loading}
                    aria-label="值编码"
                    aria-invalid={Boolean(errors["value-" + index]) || undefined}
                    placeholder="值编码，如 paid"
                    onChange={(event) => value(index, { valueCode: event.target.value })}
                  />
                  <Input
                    size="small"
                    variant="outlined"
                    value={row.cnName ?? ""}
                    maxLength={128}
                    disabled={saving || loading}
                    aria-label="值名称"
                    placeholder="值名称"
                    onChange={(event) => value(index, { cnName: event.target.value || null })}
                  />
                  <Input
                    size="small"
                    variant="outlined"
                    value={row.meaning ?? ""}
                    maxLength={500}
                    disabled={saving || loading}
                    aria-label="值说明"
                    placeholder="值说明（可选）"
                    onChange={(event) => value(index, { meaning: event.target.value || null })}
                  />
                  <Button
                    size="small"
                    variant="ghost"
                    aria-label="删除该值"
                    disabled={saving || loading}
                    className="text-[#667085] hover:text-[#d92d20]"
                    onClick={() =>
                      setInput((previous) => ({
                        ...previous,
                        values: previous.values.filter((_, position) => position !== index),
                      }))
                    }
                  >
                    <Trash2 size={14} />
                  </Button>
                </div>
                {errors["value-" + index] && (
                  <p className="mt-1 text-xs text-[#d92d20]">{errors["value-" + index]}</p>
                )}
              </div>
            ))}
          </div>
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
              disabled={conflict || loading}
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
  selectedId: string | null;
  selectedPath: string;
  unavailable: boolean;
  kindBlocked: boolean;
  version: number;
  onChanged: () => void;
  onDelete: (target: DeleteTarget) => void;
}

export default function LookupSection(props: Props) {
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [draft, setDraft] = useState<LookupDraft | null>(null);
  const key = props.unavailable
    ? null
    : keyword
      ? "kw:" + keyword + ":" + pageNo + ":" + pageSize
      : props.selectedId
        ? "cat:" + props.selectedId + ":" + pageNo + ":" + pageSize
        : null;
  const list = useKindPage(key, props.version, (signal) =>
    getLookups(
      keyword
        ? { scope: "KEYWORD", keyword, pageNo, pageSize }
        : { scope: "CATEGORY", categoryId: props.selectedId ?? "", pageNo, pageSize },
      signal,
    ),
  );
  const columns: TableColumns<StandardLookup> = [
    { title: "中文名称", dataIndex: "cnName", width: 180, ellipsis: true },
    { title: "英文编码", dataIndex: "enCode", width: 180, ellipsis: true },
    { title: "英文名称", dataIndex: "enName", width: 180, ellipsis: true },
    { title: "值数量", dataIndex: "valueCount", width: 90 },
    { title: "更新时间", dataIndex: "updateTime", width: 180 },
  ];
  if (keyword)
    columns.push({ title: "所属分类", dataIndex: "categoryPath", width: 220, ellipsis: true });
  columns.push({
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
          onClick={() => setDraft({ mode: "edit", lookup: row })}
        >
          编辑
        </Button>
        <Button
          size="small"
          variant="ghost"
          disabled={props.unavailable}
          className="px-0.5 font-normal text-[#667085] hover:text-[#d92d20]"
          onClick={() => props.onDelete({ kind: "lookup", node: row })}
        >
          删除
        </Button>
      </div>
    ),
  });
  return (
    <>
      <KindToolbar
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
        createLabel="新建标准代码"
        createDisabled={!props.selectedId || props.kindBlocked}
        hint={
          props.kindBlocked ? (
            <span className="text-xs text-[#667085]">当前标准集存放字段标准</span>
          ) : null
        }
        onCreate={() => {
          if (props.selectedId)
            setDraft({ mode: "create", categoryId: props.selectedId, path: props.selectedPath });
        }}
      />
      {list.failed && (
        <Alert>
          标准代码加载失败
          <Button size="small" onClick={props.onChanged}>
            重试
          </Button>
        </Alert>
      )}
      <div className="min-h-0 min-w-0 flex-1 overflow-auto">
        <Table<StandardLookup>
          rowKey="id"
          columns={columns}
          dataSource={list.page?.bizData ?? []}
          loading={props.unavailable || list.loading}
          size="small"
          bordered={false}
          scroll={{ x: keyword ? 1160 : 940 }}
          emptyText="暂无标准代码"
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
        <LookupDialog
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
