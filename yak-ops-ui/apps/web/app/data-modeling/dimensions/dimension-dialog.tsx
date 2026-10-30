import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Field,
  FieldLabel,
  toast,
} from "@yak-ops/yak-ui";
import { useId, useRef, useState } from "react";
import {
  classifyDimensionFailure,
  saveDimension,
} from "@/service/data-modeling/dimension-modeling";
import type {
  ConceptDimension,
  DimensionScope,
} from "@/service/data-modeling/dimension-modeling/types";
import type { PlanningTree } from "@/service/data-modeling/warehouse-planning/types";
import { InputField, SelectField, TextareaField } from "../shared/form-fields";
import { allDomainsOf, datamartsOf } from "../shared/planning-tree";
import { DimensionScopeRadio } from "../shared/scope-radio";

interface Props {
  existing: ConceptDimension | null;
  tree: PlanningTree;
  /** Preselected owner when the dialog is opened from a tree node. */
  initial?: { scope: DimensionScope; scopeId: string };
  onClose: () => void;
  onSaved: () => void;
}

type Errors = Partial<Record<"scopeId" | "name" | "enName" | "code", string>>;

const CODE = /^[a-z][a-z0-9_]{0,63}$/;

export default function DimensionDialog({ existing, tree, initial, onClose, onSaved }: Props) {
  const id = useId();
  const [scope, setScope] = useState<DimensionScope>(existing?.scope ?? initial?.scope ?? "PUBLIC");
  const [scopeId, setScopeId] = useState(
    existing?.domainId ?? existing?.datamartId ?? initial?.scopeId ?? "",
  );
  const [name, setName] = useState(existing?.name ?? "");
  const [enName, setEnName] = useState(existing?.enName ?? "");
  const [code, setCode] = useState(existing?.code ?? "");
  const [description, setDescription] = useState(existing?.description ?? "");
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [stale, setStale] = useState(false);
  const [failure, setFailure] = useState("");
  const busy = useRef(false);
  const changeScope = (next: DimensionScope) => {
    setScope(next);
    setScopeId("");
    setErrors((previous) => ({ ...previous, scopeId: undefined }));
    setConflict(false);
  };
  const scopeItems =
    scope === "PUBLIC"
      ? allDomainsOf(tree).map((domain) => ({
          value: domain.id,
          label: `${domain.categoryName} / ${domain.name}`,
        }))
      : datamartsOf(tree).map((datamart) => ({ value: datamart.id, label: datamart.name }));
  const submit = async () => {
    if (busy.current || conflict || stale) return;
    const next: Errors = {};
    if (!scopeId) next.scopeId = scope === "PUBLIC" ? "请选择数据域" : "请选择数据集市";
    if (!name.trim()) next.name = "请输入中文名";
    if (!enName.trim()) next.enName = "请输入英文名";
    if (!code.trim()) next.code = "请输入英文缩写";
    else if (!CODE.test(code.trim()))
      next.code = "须以小写字母开头，仅含小写字母、数字或下划线，最多 64 位";
    setErrors(next);
    if (Object.keys(next).length) return;
    busy.current = true;
    setSaving(true);
    setFailure("");
    try {
      await saveDimension(existing, {
        scope,
        domainId: scope === "PUBLIC" ? scopeId : null,
        datamartId: scope === "APPLICATION" ? scopeId : null,
        name: name.trim(),
        enName: enName.trim(),
        code: code.trim(),
        description: description.trim() || null,
      });
      toast.success("保存成功");
      onSaved();
    } catch (error) {
      const result = classifyDimensionFailure(error);
      if (result.kind === "field" && result.field === "code")
        setErrors({ code: "英文缩写已存在于该归属下，或不符合命名规范" });
      else if (result.kind === "field") setFailure(result.message);
      else if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing") setStale(true);
      else if (result.kind === "unknown") setFailure(result.message);
      else setFailure("保存失败，请稍后重试");
    } finally {
      busy.current = false;
      setSaving(false);
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
      <DialogContent className="w-[640px] rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {existing ? "编辑概念维度" : "新建概念维度"}
        </DialogTitle>
        <form
          noValidate
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
            <FieldLabel className="pt-1" required>
              归属类型
            </FieldLabel>
            <div>
              <DimensionScopeRadio value={scope} disabled={saving} onChange={changeScope} />
              {errors.scopeId && (
                <p className="mt-1 text-xs text-[var(--yak-components-badge-danger-text)]">
                  {errors.scopeId}
                </p>
              )}
            </div>
          </Field>
          <SelectField
            id={id + "-scope"}
            label={scope === "PUBLIC" ? "数据域" : "数据集市"}
            value={scopeId}
            required
            disabled={saving}
            error={errors.scopeId}
            items={scopeItems}
            onChange={(value) => {
              setScopeId(value);
              setErrors((previous) => ({ ...previous, scopeId: undefined }));
            }}
          />
          {scopeItems.length === 0 && (
            <Alert>
              {scope === "PUBLIC"
                ? "还没有可用的数据域，请先在「数仓规划」中创建业务分类与数据域。"
                : "还没有可用的数据集市，请先在「数仓规划」中创建应用层数据集市。"}
            </Alert>
          )}
          <InputField
            id={id + "-name"}
            label="中文名"
            value={name}
            required
            maxLength={128}
            disabled={saving}
            error={errors.name}
            onChange={setName}
          />
          <InputField
            id={id + "-en-name"}
            label="英文名"
            value={enName}
            required
            maxLength={128}
            disabled={saving}
            error={errors.enName}
            onChange={setEnName}
          />
          <InputField
            id={id + "-code"}
            label="英文缩写"
            value={code}
            required
            maxLength={64}
            disabled={saving}
            error={errors.code}
            hint={errors.code ? undefined : "同一归属下唯一，用于物理表与指标引用"}
            onChange={setCode}
          />
          <TextareaField
            id={id + "-description"}
            label="描述"
            value={description}
            rows={2}
            maxLength={500}
            disabled={saving}
            onChange={setDescription}
          />
          {conflict && <Alert>数据已变更，请关闭弹窗并刷新后重试。</Alert>}
          {stale && <Alert>所选归属已不存在，请刷新后重试。</Alert>}
          {failure && <Alert>{failure}</Alert>}
          <div className="flex justify-end gap-2 pt-2">
            <Button size="small" disabled={saving} onClick={onClose}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              type="submit"
              loading={saving}
              disabled={conflict || stale}
            >
              保存
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
