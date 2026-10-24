import {
  Alert,
  Badge,
  Button,
  Field,
  FieldError,
  FieldLabel,
  Input,
  PageHeader,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import {
  checkModelName,
  classifyModelFailure,
  getDimensions,
  getModel,
  saveModel,
} from "@/service/data-modeling/dimension-modeling";
import type {
  ConceptDimension,
  DimensionScope,
  LogicalModel,
  ModelInput,
  ModelType,
  StoragePolicy,
} from "@/service/data-modeling/dimension-modeling/types";
import {
  AGGREGATE_MODEL_TYPES,
  aggregateSupports,
  MODEL_TYPES,
  MODEL_TYPE_HINT,
  MODEL_TYPE_LABEL,
  STORAGE_POLICIES,
  STORAGE_POLICY_LABEL,
} from "@/service/data-modeling/dimension-modeling/types";
import {
  PUBLICATION_STATUS_LABEL,
  type PublicationStatus,
} from "@/service/data-modeling/dimension-modeling/publication";
import type { Layer } from "@/service/data-modeling/warehouse-planning/layers";
import { InputField, SelectField, TextareaField } from "../shared/form-fields";
import FieldEditor from "./field-editor";
import type { FieldDraft } from "./fields";
import {
  MODEL_SUFFIX_RULE,
  fromModelField,
  isValidModelSuffix,
  toModelFieldInput,
  validateFieldRows,
} from "./fields";
import { datamartsOf, domainsOf, processesOf } from "../shared/planning-tree";
import { DimensionScopeRadio } from "../shared/scope-radio";
import PublishDialog from "./publish-dialog";
import { useModelOptions } from "./state";

interface FormState {
  type: ModelType;
  scope: DimensionScope;
  layerId: string;
  categoryId: string;
  domainId: string;
  processId: string;
  datamartId: string;
  dimensionId: string;
  nameSuffix: string;
  cnName: string;
  storagePolicy: StoragePolicy;
  lifecycleDays: string;
  ownerId: string;
  description: string;
}

const PUBLICATION_TONE: Record<PublicationStatus, "info" | "success" | "danger" | "warning"> = {
  PENDING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};

/** Latest publication status of the saved model; "未发布" until the first attempt is recorded. */
function PublicationBadge({ status }: { status: PublicationStatus | null }) {
  return status ? (
    <Badge tone={PUBLICATION_TONE[status]}>{PUBLICATION_STATUS_LABEL[status]}</Badge>
  ) : (
    <Badge tone="neutral">未发布</Badge>
  );
}

const INITIAL_FORM: FormState = {
  type: "DWD",
  scope: "PUBLIC",
  layerId: "",
  categoryId: "",
  domainId: "",
  processId: "",
  datamartId: "",
  dimensionId: "",
  nameSuffix: "",
  cnName: "",
  storagePolicy: "DAILY_INCREMENTAL",
  lifecycleDays: "30",
  ownerId: "",
  description: "",
};

type NameState = "idle" | "checking" | "available" | "taken" | "error";
type FormErrors = Partial<
  Record<"layerId" | "nameSuffix" | "cnName" | "lifecycle" | "ownerId" | "ownership", string>
>;

interface DimensionState {
  items: ConceptDimension[];
  loading: boolean;
  failed: boolean;
}

function Section({
  title,
  extra,
  children,
}: {
  title: string;
  extra?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="rounded-xl border border-[var(--yak-components-panel-border)] bg-white">
      <header className="flex items-center justify-between border-b border-[#eaecf0] px-4 py-2.5">
        <h2 className="text-sm font-medium">{title}</h2>
        {extra}
      </header>
      <div className="px-4 py-4">{children}</div>
    </section>
  );
}

export default function LogicalModelEditorPage() {
  const { id } = useParams<{ id: string }>();
  const editing = Boolean(id);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const options = useModelOptions();
  const [form, setForm] = useState<FormState>(INITIAL_FORM);
  const [rows, setRows] = useState<FieldDraft[]>([]);
  const [errors, setErrors] = useState<FormErrors>({});
  const [loading, setLoading] = useState(editing);
  const [loadFailed, setLoadFailed] = useState(false);
  /**
   * The saved model, kept apart from the form so the publish dialog can name the table the server
   * will actually build instead of the name currently typed in the form.
   */
  const [savedModel, setSavedModel] = useState<LogicalModel | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [revision, setRevision] = useState<number | undefined>(undefined);
  const [unknownType, setUnknownType] = useState(false);
  const [stale, setStale] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const [attempted, setAttempted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [nameState, setNameState] = useState<NameState>("idle");
  const [suffixMismatch, setSuffixMismatch] = useState(false);
  const [dimensions, setDimensions] = useState<DimensionState>({
    items: [],
    loading: false,
    failed: false,
  });
  const busy = useRef(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);

  // A tree node opens the editor with its ownership already decided; the flag also locks it.
  const prefill = useMemo(
    () => ({
      type:
        searchParams.get("type") === "DIM"
          ? ("DIM" as const)
          : searchParams.get("type") === "DWD"
            ? ("DWD" as const)
            : null,
      scope:
        searchParams.get("scope") === "APPLICATION"
          ? ("APPLICATION" as const)
          : searchParams.get("scope") === "PUBLIC"
            ? ("PUBLIC" as const)
            : null,
      categoryId: searchParams.get("categoryId") ?? "",
      domainId: searchParams.get("domainId") ?? "",
      datamartId: searchParams.get("datamartId") ?? "",
      processId: searchParams.get("processId") ?? "",
      dimensionId: searchParams.get("dimensionId") ?? "",
      locked: searchParams.get("locked") === "1",
    }),
    [searchParams],
  );
  const ownershipLocked = !editing && prefill.locked;

  useEffect(() => {
    if (!editing || options.status !== "ready") return;
    const controller = new AbortController();
    let current = true;
    setLoading(true);
    setLoadFailed(false);
    void getModel(id!, controller.signal)
      .then((model) => {
        if (!current) return;
        setSavedModel(model);
        const layer = options.layers.find((item) => item.id === model.layerId);
        const prefix = layer ? `${layer.enCode.toLowerCase()}_` : "";
        const suffix =
          prefix && model.name.startsWith(prefix) ? model.name.slice(prefix.length) : "";
        setRevision(model.revision);
        setUnknownType(model.type === null);
        setSuffixMismatch(Boolean(prefix) && !suffix);
        setForm({
          type: model.type ?? "DWD",
          scope: model.datamartId ? "APPLICATION" : "PUBLIC",
          layerId: model.layerId,
          categoryId: model.categoryId,
          domainId: model.domainId ?? "",
          processId: model.processId ?? "",
          datamartId: model.datamartId ?? "",
          dimensionId: model.dimensionId ?? "",
          nameSuffix: suffix,
          cnName: model.cnName,
          storagePolicy: model.storagePolicy,
          lifecycleDays: String(model.lifecycleDays),
          ownerId: model.ownerId,
          description: model.description ?? "",
        });
        setRows((model.fields ?? []).map(fromModelField));
        setLoading(false);
      })
      .catch(() => {
        if (!current) return;
        setLoadFailed(true);
        setLoading(false);
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [editing, id, options.status, options.layers]);

  useEffect(() => {
    if (editing || options.status !== "ready") return;
    setForm((previous) => ({
      ...previous,
      type: prefill.type ?? previous.type,
      scope: prefill.scope ?? previous.scope,
      layerId: previous.layerId || options.layers.find((layer) => layer.enabled)?.id || "",
      categoryId: previous.categoryId || prefill.categoryId || options.tree.categories[0]?.id || "",
      domainId: previous.domainId || prefill.domainId,
      datamartId: previous.datamartId || prefill.datamartId,
      processId: previous.processId || prefill.processId,
      dimensionId: previous.dimensionId || prefill.dimensionId,
      ownerId: previous.ownerId || currentUser?.id || "",
    }));
  }, [editing, options.status, options.layers, options.tree, currentUser?.id, prefill]);

  const dimensionScopeId =
    form.type === "DIM" ? (form.scope === "PUBLIC" ? form.domainId : form.datamartId) : "";
  useEffect(() => {
    if (form.type !== "DIM" || !dimensionScopeId) {
      setDimensions({ items: [], loading: false, failed: false });
      return;
    }
    const controller = new AbortController();
    let current = true;
    setDimensions({ items: [], loading: true, failed: false });
    void getDimensions({ scope: form.scope, scopeId: dimensionScopeId }, controller.signal)
      .then((items) => {
        if (current) setDimensions({ items, loading: false, failed: false });
      })
      .catch(() => {
        if (current) setDimensions({ items: [], loading: false, failed: true });
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [form.type, form.scope, dimensionScopeId]);

  const selectedLayer = options.layers.find((layer) => layer.id === form.layerId);
  const layerSelectable = Boolean(selectedLayer?.enabled);
  const prefix = selectedLayer ? selectedLayer.enCode.toLowerCase() : "";
  const suffix = form.nameSuffix.trim();
  const suffixValid = isValidModelSuffix(suffix);

  useEffect(() => {
    if (!form.layerId || !suffixValid) {
      setNameState("idle");
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setNameState("checking");
      void checkModelName(form.layerId, suffix, id, controller.signal)
        .then((available) => setNameState(available ? "available" : "taken"))
        .catch(() => setNameState("error"));
    }, 350);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [form.layerId, suffix, suffixValid, id]);

  const domains = useMemo(
    () => domainsOf(options.tree, form.categoryId),
    [options.tree, form.categoryId],
  );
  const processes = useMemo(
    () => processesOf(options.tree, form.domainId),
    [options.tree, form.domainId],
  );
  const datamarts = useMemo(() => datamartsOf(options.tree), [options.tree]);
  const issues = useMemo(() => validateFieldRows(rows), [rows]);

  const patch = (partial: Partial<FormState>) => {
    setForm((previous) => ({ ...previous, ...partial }));
    if ("nameSuffix" in partial) setSuffixMismatch(false);
    setErrors((previous) => {
      const next = { ...previous };
      if ("layerId" in partial) delete next.layerId;
      if ("nameSuffix" in partial) delete next.nameSuffix;
      if ("cnName" in partial) delete next.cnName;
      if ("ownerId" in partial) delete next.ownerId;
      if ("lifecycleDays" in partial) delete next.lifecycle;
      if (["categoryId", "domainId", "processId", "datamartId"].some((key) => key in partial))
        delete next.ownership;
      return next;
    });
    setConflict(false);
  };

  const changeType = (type: ModelType) => {
    patch({
      type,
      processId: "",
      dimensionId: "",
      datamartId: type === "ADS" || type === "DIM" ? form.datamartId : "",
      domainId: type === "ADS" ? "" : form.domainId,
    });
  };

  const changeScope = (scope: DimensionScope) => {
    patch({
      scope,
      domainId: scope === "PUBLIC" ? form.domainId : "",
      datamartId: scope === "APPLICATION" ? form.datamartId : "",
      dimensionId: "",
    });
  };

  const validate = () => {
    const next: FormErrors = {};
    if (!form.layerId) next.layerId = "请选择数仓分层";
    else if (!layerSelectable) next.layerId = "所选分层未启用或未绑定数据源";
    if (!suffix) next.nameSuffix = "请输入表名后缀";
    else if (!suffixValid) next.nameSuffix = MODEL_SUFFIX_RULE;
    if (!form.cnName.trim()) next.cnName = "请输入表中文名";
    const lifecycle = Number(form.lifecycleDays);
    if (!Number.isInteger(lifecycle) || lifecycle < 1 || lifecycle > 36000)
      next.lifecycle = "生命周期须为 1 到 36000 之间的整数";
    if (!form.ownerId) next.ownerId = "请选择负责人";
    if (!form.categoryId) next.ownership = "请选择业务分类";
    else if (form.type === "DWD") {
      if (!form.domainId) next.ownership = "请选择数据域";
      else if (!form.processId) next.ownership = "请选择业务过程";
    } else if (form.scope === "PUBLIC") {
      if (!form.domainId) next.ownership = "请选择数据域";
      else if (!form.dimensionId) next.ownership = "公有层维度表必须绑定一个概念维度";
    } else if (!form.datamartId) next.ownership = "请选择数据集市";
    return next;
  };

  const submit = async () => {
    if (busy.current || conflict || stale || unknownType || suffixMismatch) return;
    setAttempted(true);
    setFailure("");
    const next = validate();
    setErrors(next);
    if (Object.keys(next).length || issues.length) return;
    if (
      rows.some(
        (row) =>
          row.aggregateType && row.dataType && !aggregateSupports(row.aggregateType, row.dataType),
      )
    ) {
      setFailure("聚合方式与字段类型不匹配：SUM/AVG 只能用于 BIGINT 或 DECIMAL");
      return;
    }
    if (form.type === "DWS" && !rows.some((row) => row.aggregateType)) {
      setFailure("汇总表至少需要一个带聚合方式的度量字段");
      return;
    }
    if (nameState === "taken") {
      setErrors({ nameSuffix: "表名在当前工作空间已存在" });
      return;
    }
    const input: ModelInput = {
      type: form.type,
      layerId: form.layerId,
      categoryId: form.categoryId,
      processId: form.type === "DWD" || form.type === "DWS" ? form.processId || null : null,
      domainId:
        form.type === "DWD" || form.type === "DWS" || form.scope === "PUBLIC"
          ? form.domainId || null
          : null,
      datamartId:
        form.type === "ADS" || (form.type === "DIM" && form.scope === "APPLICATION")
          ? form.datamartId || null
          : null,
      dimensionId: form.type === "DIM" ? form.dimensionId || null : null,
      nameSuffix: suffix,
      cnName: form.cnName.trim(),
      storagePolicy: form.storagePolicy,
      lifecycleDays: Number(form.lifecycleDays),
      ownerId: form.ownerId,
      description: form.description.trim() || null,
      fields: rows.map(toModelFieldInput),
    };
    busy.current = true;
    setSaving(true);
    try {
      const saved = await saveModel(id ?? null, input, revision);
      if (!active.current) return;
      toast.success(editing ? "保存成功" : "创建成功");
      if (editing) {
        setSavedModel(saved);
        setRevision(saved.revision);
        setRows((saved.fields ?? []).map(fromModelField));
        setSuffixMismatch(false);
      } else
        navigate(`/data-modeling/logical-models/${encodeURIComponent(saved.id)}`, {
          replace: true,
        });
    } catch (error) {
      if (!active.current) return;
      const result = classifyModelFailure(error);
      if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing") setStale(true);
      else if (result.kind === "unknown-type") setUnknownType(true);
      else if (result.kind === "field") {
        if (result.field === "nameSuffix") setErrors({ nameSuffix: result.message });
        else if (result.field === "layer") setErrors({ layerId: result.message });
        else if (result.field === "ownership") setFailure(result.message);
        else setFailure(`字段校验未通过：${result.message}`);
      } else setFailure(result.message);
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
    }
  };

  const layerItems = options.layers.map((layer: Layer) => ({
    value: layer.id,
    label: `${layer.enCode} · ${layer.cnName}${layer.enabled ? "" : "（未启用）"}`,
    disabled: !layer.enabled,
  }));
  const ownerItems = options.owners.map((owner) => ({
    value: owner.userId,
    label: owner.displayName,
    disabled: false,
  }));
  if (form.ownerId && !options.owners.some((owner) => owner.userId === form.ownerId))
    ownerItems.push({ value: form.ownerId, label: `${form.ownerId}（已不可选）`, disabled: true });
  const dimensionItems = dimensions.items.map((item) => ({
    value: item.id,
    label: `${item.name}（${item.code}）`,
  }));
  if (form.type === "DIM" && form.scope === "APPLICATION")
    dimensionItems.unshift({ value: "", label: "不绑定概念维度" });
  if (form.dimensionId && !dimensions.items.some((item) => item.id === form.dimensionId))
    dimensionItems.push({ value: form.dimensionId, label: "当前绑定（不在列表中）" });
  const disabled = saving || loading || unknownType || stale;

  /**
   * Re-reads the saved model after a publication. The model itself did not change, but its latest
   * status did, and the header renders that status.
   */
  const refreshSavedModel = () => {
    if (!savedModel) return;
    void getModel(savedModel.id)
      .then((model) => {
        if (!active.current) return;
        setSavedModel(model);
      })
      .catch(() => undefined);
  };

  if (loadFailed)
    return (
      <div className="flex h-full flex-col">
        <PageHeader title="逻辑模型" bordered className="bg-white px-6" />
        <div className="p-6">
          <Alert>
            模型加载失败，可能已被删除。
            <Button size="small" onClick={() => navigate("/data-modeling/dimension-modeling")}>
              返回列表
            </Button>
          </Alert>
        </div>
      </div>
    );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title={editing ? "编辑逻辑模型" : "新建逻辑模型"}
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <div className="flex items-center gap-2">
            {editing && savedModel && !unknownType && (
              <>
                <PublicationBadge status={savedModel.latestPublicationStatus} />
                <Button size="small" disabled={saving} onClick={() => setPublishing(true)}>
                  发布
                </Button>
              </>
            )}
            <Button size="small" onClick={() => navigate("/data-modeling/dimension-modeling")}>
              返回列表
            </Button>
          </div>
        }
      />
      <div className="min-h-0 flex-1 overflow-auto px-6 pb-24 pt-5 max-md:px-4">
        <div className="mx-auto flex max-w-[1280px] flex-col gap-4">
          {options.status === "failed" && (
            <Alert>
              分层、业务分类或成员加载失败
              <Button size="small" onClick={options.reload}>
                重试
              </Button>
            </Alert>
          )}
          {conflict && <Alert>数据已变更，请刷新后重新编辑。</Alert>}
          {stale && <Alert>模型已不存在，请返回列表刷新。</Alert>}
          {unknownType && <Alert>该模型的类型无法识别，不允许通过界面编辑。</Alert>}
          {failure && <Alert>{failure}</Alert>}
          {suffixMismatch && (
            <Alert>当前表名与所选分层的前缀不一致，请重新填写表名后缀后再保存。</Alert>
          )}
          {options.status === "ready" && !options.layers.some((layer) => layer.enabled) && (
            <Alert>当前工作空间还没有启用的数仓分层，请先到「数仓规划」创建并启用分层。</Alert>
          )}
          {options.status === "ready" && options.tree.categories.length === 0 && (
            <Alert>当前工作空间还没有业务分类，请先到「数仓规划」创建业务分类与数据域。</Alert>
          )}
          {options.status === "ready" &&
            form.type === "DIM" &&
            form.scope === "APPLICATION" &&
            datamarts.length === 0 && (
              <Alert>当前工作空间还没有数据集市，请先到「数仓规划」创建应用层数据集市。</Alert>
            )}
          <Section title="基本信息">
            <div className="grid grid-cols-1 gap-x-10 gap-y-3 md:grid-cols-2">
              {unknownType ? (
                <InputField
                  id="model-type"
                  label="模型类型"
                  value="未知类型"
                  required
                  readOnly
                  hint="存储的类型不在当前版本支持范围内"
                />
              ) : (
                <SelectField
                  id="model-type"
                  label="模型类型"
                  value={form.type}
                  required
                  disabled={disabled || editing || ownershipLocked}
                  hint={
                    editing
                      ? "类型创建后不可变更"
                      : ownershipLocked
                        ? "从概念维度进入，类型与归属已锁定"
                        : MODEL_TYPE_HINT[form.type]
                  }
                  items={MODEL_TYPES.map((value) => ({ value, label: MODEL_TYPE_LABEL[value] }))}
                  onChange={(value) => {
                    // The items come from MODEL_TYPES, so an unknown value can only mean a
                    // desynchronized option list; ignore it instead of coercing to DWD.
                    const next = MODEL_TYPES.find((candidate) => candidate === value);
                    if (next) changeType(next);
                  }}
                />
              )}
              <SelectField
                id="model-layer"
                label="数仓分层"
                value={form.layerId}
                required
                disabled={disabled}
                error={errors.layerId}
                hint={selectedLayer ? `表名前缀 ${prefix}_` : "分层决定表名前缀"}
                items={layerItems}
                onChange={(value) => patch({ layerId: value })}
              />
              {form.type === "DIM" && (
                <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
                  <FieldLabel className="pt-1" required>
                    归属类型
                  </FieldLabel>
                  <DimensionScopeRadio
                    value={form.scope}
                    disabled={disabled}
                    onChange={changeScope}
                  />
                </Field>
              )}
              {form.type === "DWD" || form.type === "DWS" || form.scope === "PUBLIC" ? (
                <>
                  <SelectField
                    id="model-category"
                    label="业务分类"
                    value={form.categoryId}
                    required
                    disabled={disabled || ownershipLocked}
                    items={options.tree.categories.map((category) => ({
                      value: category.id,
                      label: category.name,
                    }))}
                    onChange={(value) =>
                      patch({ categoryId: value, domainId: "", processId: "", dimensionId: "" })
                    }
                  />
                  <SelectField
                    id="model-domain"
                    label="数据域"
                    value={form.domainId}
                    required
                    disabled={disabled || ownershipLocked}
                    items={domains.map((domain) => ({ value: domain.id, label: domain.name }))}
                    onChange={(value) => patch({ domainId: value, processId: "", dimensionId: "" })}
                  />
                </>
              ) : (
                <SelectField
                  id="model-datamart"
                  label="数据集市"
                  value={form.datamartId}
                  required
                  disabled={disabled || ownershipLocked}
                  items={datamarts.map((datamart) => ({
                    value: datamart.id,
                    label: datamart.name,
                  }))}
                  onChange={(value) => patch({ datamartId: value, dimensionId: "" })}
                />
              )}
              {(form.type === "DWD" || form.type === "DWS") && (
                <SelectField
                  id="model-process"
                  label="业务过程"
                  value={form.processId}
                  required
                  disabled={disabled || ownershipLocked}
                  items={processes.map((process) => ({ value: process.id, label: process.name }))}
                  onChange={(value) => patch({ processId: value })}
                />
              )}
              {form.type === "DIM" && (
                <SelectField
                  id="model-dimension"
                  label="概念维度"
                  value={form.dimensionId}
                  required={form.scope === "PUBLIC"}
                  disabled={disabled || !dimensionScopeId || ownershipLocked}
                  hint={
                    dimensions.failed
                      ? "概念维度加载失败"
                      : dimensionScopeId && dimensions.loading
                        ? "概念维度加载中"
                        : form.scope === "PUBLIC" && dimensions.items.length === 0
                          ? "该数据域下还没有概念维度，请先到「概念维度」创建"
                          : undefined
                  }
                  items={dimensionItems}
                  onChange={(value) => patch({ dimensionId: value })}
                />
              )}
              {(form.type === "ADS" || (form.type === "DIM" && form.scope === "APPLICATION")) && (
                <SelectField
                  id="model-archive-category"
                  label="归档分类"
                  value={form.categoryId}
                  required
                  disabled={disabled}
                  hint="应用层模型同样记录业务分类，仅用于归档"
                  items={options.tree.categories.map((category) => ({
                    value: category.id,
                    label: category.name,
                  }))}
                  onChange={(value) => patch({ categoryId: value })}
                />
              )}
              <Field
                className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
                invalid={Boolean(errors.nameSuffix)}
              >
                <FieldLabel htmlFor="model-suffix" className="pt-1" required>
                  表名
                </FieldLabel>
                <div>
                  <div className="flex items-center gap-1">
                    <span className="shrink-0 font-mono text-xs text-[#667475]">
                      {prefix ? `${prefix}_` : "—"}
                    </span>
                    <Input
                      id="model-suffix"
                      size="small"
                      variant="outlined"
                      value={form.nameSuffix}
                      maxLength={41}
                      disabled={disabled}
                      placeholder="order_detail"
                      aria-invalid={Boolean(errors.nameSuffix) || undefined}
                      onChange={(event) => patch({ nameSuffix: event.target.value })}
                    />
                  </div>
                  <FieldError match={Boolean(errors.nameSuffix)}>{errors.nameSuffix}</FieldError>
                  <p className="mt-1 text-[11px] leading-4 text-[#667085]">
                    {prefix && suffix
                      ? `完整表名：${prefix}_${suffix}`
                      : "表名由分层前缀与后缀拼成"}
                    {nameState === "checking" && " · 校验中"}
                    {nameState === "available" && " · 可用"}
                    {nameState === "taken" && <span className="text-[#b42318]"> · 已存在</span>}
                    {nameState === "error" && " · 名称校验失败"}
                  </p>
                </div>
              </Field>
              <InputField
                id="model-cn-name"
                label="表中文名"
                value={form.cnName}
                required
                maxLength={128}
                disabled={disabled}
                error={errors.cnName}
                onChange={(value) => patch({ cnName: value })}
              />
              <SelectField
                id="model-storage"
                label="存储策略"
                value={form.storagePolicy}
                required
                disabled={disabled}
                items={STORAGE_POLICIES.map((value) => ({
                  value,
                  label: STORAGE_POLICY_LABEL[value],
                }))}
                onChange={(value) =>
                  patch({
                    storagePolicy: value === "DAILY_FULL" ? "DAILY_FULL" : "DAILY_INCREMENTAL",
                  })
                }
              />
              <InputField
                id="model-lifecycle"
                label="生命周期（天）"
                value={form.lifecycleDays}
                required
                maxLength={5}
                disabled={disabled}
                error={errors.lifecycle}
                onChange={(value) => patch({ lifecycleDays: value.replace(/[^\d]/g, "") })}
              />
              <SelectField
                id="model-owner"
                label="负责人"
                value={form.ownerId}
                required
                disabled={disabled || options.status !== "ready"}
                error={errors.ownerId}
                items={ownerItems}
                onChange={(value) => patch({ ownerId: value })}
              />
              <div className="md:col-span-2">
                <TextareaField
                  id="model-description"
                  label="描述"
                  value={form.description}
                  rows={2}
                  maxLength={500}
                  disabled={disabled}
                  onChange={(value) => patch({ description: value })}
                />
              </div>
            </div>
            {errors.ownership && <p className="mt-2 text-xs text-[#b42318]">{errors.ownership}</p>}
          </Section>
          <Section
            title="字段定义"
            extra={<span className="text-xs text-[#667475]">整表保存，字段顺序即写入顺序</span>}
          >
            <FieldEditor
              rows={rows}
              onChange={setRows}
              issues={attempted ? issues : []}
              disabled={disabled}
              allowAggregate={AGGREGATE_MODEL_TYPES.has(form.type)}
              requireMeasure={form.type === "DWS"}
            />
          </Section>
        </div>
      </div>
      <div className="flex items-center justify-end gap-2 border-t border-[#eaecf0] bg-white px-6 py-3">
        <Button size="small" onClick={() => navigate("/data-modeling/dimension-modeling")}>
          取消
        </Button>
        <Button
          size="small"
          variant="primary"
          loading={saving}
          disabled={disabled || suffixMismatch || nameState === "taken"}
          onClick={() => void submit()}
        >
          保存
        </Button>
      </div>
      {publishing && savedModel && (
        <PublishDialog
          model={savedModel}
          onClose={() => setPublishing(false)}
          onPublished={refreshSavedModel}
        />
      )}
    </div>
  );
}
