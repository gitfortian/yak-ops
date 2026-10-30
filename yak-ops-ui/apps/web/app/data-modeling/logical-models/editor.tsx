import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Field,
  FieldError,
  FieldLabel,
  Input,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { getNamingEntries } from "@/service/data-modeling/data-standards";
import type { StandardNamingEntry } from "@/service/data-modeling/data-standards/types";
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
import {
  defaultLayerForType,
  layerMatchesType,
  layersForType,
  MODEL_LAYER_CODE,
  modelTypeFromCode,
} from "./layer-binding";
import {
  composeSuffix,
  decomposeSuffix,
  qualifierMaxLength,
  rootSearchSegment,
  sanitizeQualifier,
  stripLayerPrefix,
} from "./naming-composer";
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
  Record<
    "layerId" | "nameSuffix" | "cnName" | "lifecycle" | "ownerId" | "ownership" | "fields",
    string
  >
>;

/**
 * Everything a save would send, in a comparable form. Editor-only field decorations (client keys,
 * import notes) stay out, so restyling a row is not mistaken for an edit worth warning about.
 */
function formSnapshot(form: FormState, rows: FieldDraft[]): string {
  return JSON.stringify([form, rows.map(toModelFieldInput)]);
}

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
    <Card>
      <CardHeader title={title} extra={extra} />
      <CardContent>{children}</CardContent>
    </Card>
  );
}

export default function LogicalModelEditorPage() {
  const { id } = useParams<{ id: string }>();
  const editing = Boolean(id);
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { currentUser, loading: authLoading } = useAuth();
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
  /** Snapshot of the state the page opened or last saved with; null until one is known. */
  const [baseline, setBaseline] = useState<string | null>(null);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const baselineKnown = useRef(false);
  const [nameState, setNameState] = useState<NameState>("idle");
  const [suffixMismatch, setSuffixMismatch] = useState(false);
  const [rootId, setRootId] = useState("");
  const [qualifier, setQualifier] = useState("");
  const [modelRootId, setModelRootId] = useState<string | null>(null);
  const [rootOptions, setRootOptions] = useState<StandardNamingEntry[]>([]);
  const [rootState, setRootState] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [rootVersion, setRootVersion] = useState(0);
  const decomposedFor = useRef<string | null>(null);
  /**
   * The suffix exactly as loaded from the server. Composer recovery reads this snapshot instead
   * of the live form so a slow word-root list can never read — and then blank — its own source
   * of truth.
   */
  const savedSuffixRef = useRef("");
  const [dimensions, setDimensions] = useState<DimensionState>({
    items: [],
    loading: false,
    failed: false,
  });
  const busy = useRef(false);
  const active = useRef(true);
  const formRef = useRef(form);
  formRef.current = form;
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);

  // A tree node opens the editor with its ownership already decided; the flag also locks it.
  const prefill = useMemo(
    () => ({
      // Every kind a tree node can name must decode, not only the first two: an undecoded DWS or
      // ADS link silently left the form on its default detail table.
      type: modelTypeFromCode(searchParams.get("type")),
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
  /**
   * A link that names no kind this build can bind — `type=ODS`, a typo, a removed kind. The form
   * still opens on its default, but the kind field states which value it refused instead of
   * silently presenting a detail table as if that is what was asked for.
   */
  const rejectedEntryType =
    !editing && prefill.type === null && searchParams.get("type")
      ? `链接指定的类型「${searchParams.get("type")}」不被支持，已按默认类型打开，请确认模型类型`
      : "";

  // Navigating between models must not inherit the previous model's composer state.
  useEffect(() => {
    savedSuffixRef.current = "";
    decomposedFor.current = null;
    baselineKnown.current = false;
    setBaseline(null);
    setConfirmLeave(false);
    setRootId("");
    setQualifier("");
    setModelRootId(null);
    setRootOptions([]);
    setRootState("idle");
  }, [id]);

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
        savedSuffixRef.current = suffix;
        setModelRootId(model.namingRootWordId);
        const loadedForm: FormState = {
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
        };
        setForm(loadedForm);
        const loaded = (model.fields ?? []).map(fromModelField);
        setRows(loaded);
        // The just-loaded model is the state to compare later edits against.
        baselineKnown.current = true;
        setBaseline(formSnapshot(loadedForm, loaded));
        // A model can already be persisted in a shape publication will refuse. Its rows came from the
        // server rather than from the user's keystrokes, so show the violations at once; rows still
        // being written stay quiet until the first save attempt.
        setAttempted(validateFieldRows(loaded).length > 0);
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
    const previous = formRef.current;
    // The kind fixes which layer is legal, so the default layer follows the prefilled kind.
    const type = prefill.type ?? previous.type;
    const next: FormState = {
      ...previous,
      type,
      scope: prefill.scope ?? previous.scope,
      layerId: previous.layerId || defaultLayerForType(options.layers, type)?.id || "",
      categoryId: previous.categoryId || prefill.categoryId || options.tree.categories[0]?.id || "",
      domainId: previous.domainId || prefill.domainId,
      datamartId: previous.datamartId || prefill.datamartId,
      processId: previous.processId || prefill.processId,
      dimensionId: previous.dimensionId || prefill.dimensionId,
      ownerId: previous.ownerId || currentUser?.id || "",
    };
    setForm(next);
    // A create page is clean once its defaults settle; later arrivals of the same options must not
    // re-baseline a form the user has already started filling in. The owner default comes from the
    // session, so waiting for auth keeps that late arrival from marking an untouched page dirty.
    if (!baselineKnown.current && !authLoading) {
      baselineKnown.current = true;
      setBaseline(formSnapshot(next, []));
    }
  }, [
    editing,
    options.status,
    options.layers,
    options.tree,
    currentUser?.id,
    authLoading,
    prefill,
  ]);

  /**
   * A public-layer dimension is the only kind whose ownership is chosen by scope; every other kind
   * is owned through its own type. Reading scope directly made an application table inherit the
   * public default and then be asked for a concept dimension its form never renders.
   */
  const publicDimension = form.type === "DIM" && form.scope === "PUBLIC";
  const dimensionScopeId =
    form.type === "DIM" ? (publicDimension ? form.domainId : form.datamartId) : "";
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
  /**
   * Layers the current kind may live in. Without a layer carrying the kind's code the select keeps
   * every layer and the page states the gap, because an empty select would strand the user here.
   */
  const boundLayers = layersForType(options.layers, form.type);
  // An unreadable type carries no kind to bind to, so the page stops guessing and says so instead.
  const layerBindingActive = !unknownType && boundLayers.length > 0;
  const layerMismatch =
    layerBindingActive &&
    selectedLayer !== undefined &&
    !layerMatchesType(selectedLayer, form.type);
  const layerRuleMessage = `${MODEL_TYPE_LABEL[form.type]}只能建在 ${MODEL_LAYER_CODE[form.type]} 层`;

  useEffect(() => {
    if (!form.layerId || !suffixValid) {
      setNameState("idle");
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setNameState("checking");
      void checkModelName(form.layerId, suffix, id, controller.signal)
        .then((available) => {
          // A late answer for an earlier suffix must not overwrite the state of the current one.
          if (!controller.signal.aborted) setNameState(available ? "available" : "taken");
        })
        .catch(() => {
          if (!controller.signal.aborted) setNameState("error");
        });
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

  // DIM and ADS are exempt from the layer naming rule; only a governed layer with a public-layer
  // model switches the free-form suffix input into the composed control.
  const composerActive =
    Boolean(selectedLayer?.namingRuleEnabled) && (form.type === "DWD" || form.type === "DWS");
  const processCode = processes.find((item) => item.id === form.processId)?.code ?? "";
  // The qualifier input cannot push the composed name past the backend's 41-character cap.
  const qualifierCap = composerActive
    ? qualifierMaxLength(
        prefix,
        rootOptions.find((item) => item.id === rootId)?.enCode ?? "",
        processCode,
      )
    : 41;

  useEffect(() => {
    if (!composerActive) return;
    const controller = new AbortController();
    let current = true;
    setRootState("loading");
    void getNamingEntries("", 1, 100, controller.signal)
      .then(async (page) => {
        if (!current) return;
        let entries = page.bizData;
        const savedSuffix = savedSuffixRef.current;
        // A saved root beyond the first page is resolved by searching its own leading segment,
        // so reopening an old model still shows which word root it was composed from.
        const segment = modelRootId && savedSuffix ? rootSearchSegment(savedSuffix) : "";
        if (modelRootId && !entries.some((entry) => entry.id === modelRootId) && segment) {
          try {
            const found = await getNamingEntries(segment, 1, 100, controller.signal);
            if (!current) return;
            const known = new Set(entries.map((entry) => entry.id));
            entries = entries.concat(found.bizData.filter((entry) => !known.has(entry.id)));
          } catch {
            // The primary list still stands; the composer simply starts unselected.
          }
        }
        setRootOptions(entries);
        setRootState("ready");
      })
      .catch(() => {
        if (current) setRootState("error");
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [composerActive, modelRootId, rootVersion]);

  // Recovers the saved composition once per model: the root is reselected and the saved suffix
  // snapshot is split back into the qualifier tail. Recovery waits until the process code is
  // resolvable — a not-yet-loaded planning tree must not consume the one-shot guard — and an
  // unresolvable root leaves the composer unselected instead of pretending a match.
  useEffect(() => {
    if (!modelRootId || rootState !== "ready") return;
    if (decomposedFor.current === modelRootId) return;
    const entry = rootOptions.find((item) => item.id === modelRootId);
    if (!entry) {
      decomposedFor.current = modelRootId;
      return;
    }
    const savedProcessCode =
      processes.find((item) => item.id === formRef.current.processId)?.code ?? "";
    if (!savedProcessCode) return;
    decomposedFor.current = modelRootId;
    setRootId(modelRootId);
    setQualifier(decomposeSuffix(savedSuffixRef.current, entry.enCode, savedProcessCode));
  }, [modelRootId, rootState, rootOptions, processes]);

  // The suffix is fully derived while the composer is active; switching the process or root
  // re-composes it live, and the payload keeps carrying a single nameSuffix. Until the saved
  // composition has been recovered for an existing model the derivation stays off, so the
  // loaded suffix is never blanked by an empty composer state.
  useEffect(() => {
    if (!composerActive || !rootId) return;
    if (modelRootId && decomposedFor.current !== modelRootId) return;
    const entry = rootOptions.find((item) => item.id === rootId);
    const composed = composeSuffix(entry?.enCode ?? "", processCode, qualifier);
    setForm((previous) =>
      previous.nameSuffix === composed ? previous : { ...previous, nameSuffix: composed },
    );
    setSuffixMismatch(false);
  }, [composerActive, rootId, qualifier, processCode, rootOptions, modelRootId]);

  const patch = (partial: Partial<FormState>) => {
    setForm((previous) => {
      const next = { ...previous, ...partial };
      // A prefix pasted before its layer was known would otherwise be stored twice: the layer
      // supplies it at save time, so it is dropped from the suffix the moment the layer changes.
      if (next.layerId !== previous.layerId) {
        const layer = options.layers.find((item) => item.id === next.layerId);
        next.nameSuffix = stripLayerPrefix(
          next.nameSuffix,
          layer ? layer.enCode.toLowerCase() : "",
        );
      }
      return next;
    });
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

  /**
   * Drops a layer prefix that arrived without a keystroke the input could react to — a paste while
   * the layer was still unset, or an autofilled value. Only patches when it actually changes, so a
   * blur never clears an error message the user still needs to read.
   */
  const normalizeSuffix = () => {
    const normalized = stripLayerPrefix(form.nameSuffix, prefix);
    if (normalized !== form.nameSuffix) patch({ nameSuffix: normalized });
  };

  const changeType = (type: ModelType) => {
    // The kind decides which layer is legal: keep the current one when it already matches, else
    // move to the kind's own layer so the prefix and the kind cannot disagree.
    const fallback = defaultLayerForType(options.layers, type);
    patch({
      type,
      layerId: layerMatchesType(selectedLayer, type) || !fallback ? form.layerId : fallback.id,
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
    else if (layerMismatch) next.layerId = layerRuleMessage;
    if (composerActive) {
      if (!rootId) next.nameSuffix = "请选择词根";
      else if (!suffix) next.nameSuffix = "请选择业务过程";
      else if (!suffixValid) next.nameSuffix = "组合表名超出 41 个字符上限";
    } else {
      if (!suffix) next.nameSuffix = "请输入表名后缀";
      else if (!suffixValid) next.nameSuffix = MODEL_SUFFIX_RULE;
    }
    if (!form.cnName.trim()) next.cnName = "请输入表中文名";
    const lifecycle = Number(form.lifecycleDays);
    if (!Number.isInteger(lifecycle) || lifecycle < 1 || lifecycle > 36000)
      next.lifecycle = "生命周期须为 1 到 36000 之间的整数";
    if (!form.ownerId) next.ownerId = "请选择负责人";
    if (rows.length === 0) next.fields = "请至少添加一个字段，模型发布时字段决定物理表结构";
    if (!form.categoryId) next.ownership = "请选择业务分类";
    // DWD and DWS both hang below a business process; DIM and ADS split on their own scope.
    else if (form.type === "DWD" || form.type === "DWS") {
      if (!form.domainId) next.ownership = "请选择数据域";
      else if (!form.processId) next.ownership = "请选择业务过程";
    } else if (publicDimension) {
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
        form.type === "DWD" || form.type === "DWS" || publicDimension
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
        const savedRows = (saved.fields ?? []).map(fromModelField);
        // Mirror what the server stored, including the trimmed Chinese name, so the page is not
        // left comparing itself against its own save and warning about edits nobody made.
        const savedForm: FormState = {
          ...formRef.current,
          cnName: saved.cnName,
          description: saved.description ?? "",
        };
        setForm(savedForm);
        setRows(savedRows);
        setSuffixMismatch(false);
        setBaseline(formSnapshot(savedForm, savedRows));
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
        // The server message already names what failed, so it is shown as written instead of
        // collecting a second prefix in front of it.
        else setFailure(result.message);
      } else setFailure(result.message);
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
    }
  };

  /**
   * Only the kind's own layer stays selectable: the layer supplies the table prefix, so filing a
   * detail table under the dimension layer would name it as something it is not. The other layers
   * stay listed but disabled, because a controlled select drops its value when the option is
   * removed from the list, which would blank the layer the moment the kind changes.
   */
  const layerItems = options.layers.map((layer: Layer) => ({
    value: layer.id,
    label: `${layer.enCode} · ${layer.cnName}${layer.enabled ? "" : "（未启用）"}`,
    disabled: !layer.enabled || (layerBindingActive && !layerMatchesType(layer, form.type)),
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
  const rootItems = rootOptions.map((entry) => ({
    value: entry.id,
    label: `${entry.enCode}（${entry.cnName}）`,
  }));
  if (form.type === "DIM" && form.scope === "APPLICATION")
    dimensionItems.unshift({ value: "", label: "不绑定概念维度" });
  if (form.dimensionId && !dimensions.items.some((item) => item.id === form.dimensionId))
    dimensionItems.push({ value: form.dimensionId, label: "当前绑定（不在列表中）" });
  /**
   * DWD and DWS hang below a business process, so a new model has no identity without one. An
   * existing model stays savable, otherwise a record that arrived without a process could never be
   * repaired from this page.
   */
  const processMissing = (form.type === "DWD" || form.type === "DWS") && !form.processId;
  const processBlocked = processMissing && !editing;
  const disabled = saving || loading || unknownType || stale;
  /**
   * Leaving with unsaved edits throws them away, so both the in-app back buttons and the browser's
   * own close/reload ask first. A save resets the baseline, so a just-saved page leaves silently.
   */
  const dirty = baseline !== null && formSnapshot(form, rows) !== baseline;
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const requestLeave = () => {
    if (dirty) setConfirmLeave(true);
    else navigate("/data-modeling/dimension-modeling");
  };

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
        <PageHeader title="逻辑模型" bordered className="bg-[var(--yak-color-background)] px-6" />
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
        className="bg-[var(--yak-color-background)] px-6 max-md:px-4"
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
            <Button size="small" onClick={requestLeave}>
              返回列表
            </Button>
          </div>
        }
      />
      <div className="min-h-0 flex-1 overflow-auto px-6 pb-6 pt-5 max-md:px-4">
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
          {options.status === "ready" && !unknownType && !layerBindingActive && (
            <Alert>
              {`当前工作空间没有编码为 ${MODEL_LAYER_CODE[form.type]} 的数仓分层，${layerRuleMessage}。请先到「数仓规划」创建该分层或修正分层编码，否则表名前缀无法体现它所在的分层。`}
            </Alert>
          )}
          {layerMismatch && (
            <Alert>{`该模型当前所在的 ${selectedLayer?.enCode} 层与类型不匹配，${layerRuleMessage}，请改选分层后再保存。`}</Alert>
          )}
          {options.status === "ready" && options.tree.categories.length === 0 && (
            <Alert>当前工作空间还没有业务分类，请先到「数仓规划」创建业务分类与数据域。</Alert>
          )}
          {options.status === "ready" &&
            (form.type === "ADS" || (form.type === "DIM" && !publicDimension)) &&
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
                      : rejectedEntryType ||
                        (ownershipLocked ? "从概念维度进入，类型与归属已锁定" : undefined)
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
                hint={
                  layerMismatch
                    ? `${layerRuleMessage}，请重新选择`
                    : selectedLayer
                      ? `表名前缀 ${prefix}_`
                      : undefined
                }
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
              {form.type === "DWD" || form.type === "DWS" || publicDimension ? (
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
                  hint={
                    form.domainId && processes.length === 0
                      ? "该数据域下还没有业务过程，请先到「数仓规划」创建"
                      : undefined
                  }
                  items={processes.map((process) => ({ value: process.id, label: process.name }))}
                  onChange={(value) => patch({ processId: value })}
                />
              )}
              {form.type === "DIM" && (
                <SelectField
                  id="model-dimension"
                  label="概念维度"
                  value={form.dimensionId}
                  required={publicDimension}
                  disabled={disabled || !dimensionScopeId || ownershipLocked}
                  hint={
                    dimensions.failed
                      ? "概念维度加载失败"
                      : dimensionScopeId && dimensions.loading
                        ? "概念维度加载中"
                        : publicDimension && dimensions.items.length === 0
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
                  hint="仅用于资产归档"
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
                <FieldLabel
                  htmlFor={composerActive ? "model-qualifier" : "model-suffix"}
                  className="pt-1"
                  required
                >
                  表名
                </FieldLabel>
                <div>
                  {composerActive ? (
                    <>
                      <div className="flex flex-wrap items-center gap-1">
                        <span className="shrink-0 font-mono text-xs text-[var(--yak-color-text-secondary)]">
                          {prefix ? `${prefix}_` : "—"}
                        </span>
                        <Select
                          size="small"
                          value={rootId || ""}
                          items={rootItems}
                          disabled={disabled || rootState !== "ready"}
                          onValueChange={(value) => {
                            // A manual pick takes over from pending recovery: it consumes the
                            // one-shot guard so recovery can neither override it nor keep the
                            // derivation blocked.
                            decomposedFor.current = modelRootId;
                            setRootId(value ?? "");
                          }}
                        >
                          <SelectTrigger
                            className="w-44"
                            variant="outlined"
                            aria-label="表名词根"
                            aria-invalid={Boolean(errors.nameSuffix) || undefined}
                          >
                            <SelectValue
                              placeholder={
                                rootState === "loading"
                                  ? "词根加载中"
                                  : rootState === "error"
                                    ? "词根加载失败"
                                    : "选择词根"
                              }
                            />
                          </SelectTrigger>
                          <SelectContent>
                            {rootItems.map((item) => (
                              <SelectItem key={item.value} value={item.value}>
                                <SelectItemText>{item.label}</SelectItemText>
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <span
                          className="shrink-0 font-mono text-xs text-[var(--yak-color-text-secondary)]"
                          title="业务过程编码"
                        >
                          _{processCode || "过程编码"}
                        </span>
                        <Input
                          id="model-qualifier"
                          size="small"
                          variant="outlined"
                          value={qualifier}
                          maxLength={qualifierCap}
                          disabled={disabled}
                          placeholder="限定词（可选）"
                          aria-label="表名限定词"
                          className="w-40"
                          onChange={(event) => setQualifier(sanitizeQualifier(event.target.value))}
                          onBlur={() => setQualifier((value) => stripLayerPrefix(value, prefix))}
                        />
                        {rootState === "error" && (
                          <Button
                            size="small"
                            variant="ghost"
                            className="shrink-0 font-normal"
                            onClick={() => setRootVersion((value) => value + 1)}
                          >
                            重试
                          </Button>
                        )}
                      </div>
                      <FieldError match={Boolean(errors.nameSuffix)}>
                        {errors.nameSuffix}
                      </FieldError>
                      <p className="mt-1 text-xs leading-4 text-[var(--yak-color-text-secondary)]">
                        {prefix && suffix ? `完整表名：${prefix}_${suffix}` : null}
                        {nameState === "checking" && " · 校验中"}
                        {nameState === "available" && " · 可用"}
                        {nameState === "taken" && (
                          <span className="text-[var(--yak-components-badge-danger-text)]">
                            {" "}
                            · 已存在
                          </span>
                        )}
                        {nameState === "error" && " · 名称校验失败"}
                      </p>
                    </>
                  ) : (
                    <>
                      <div className="flex items-center gap-1">
                        <span className="shrink-0 font-mono text-xs text-[var(--yak-color-text-secondary)]">
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
                          onBlur={normalizeSuffix}
                          onChange={(event) =>
                            patch({
                              nameSuffix: stripLayerPrefix(event.target.value, prefix),
                            })
                          }
                        />
                      </div>
                      <FieldError match={Boolean(errors.nameSuffix)}>
                        {errors.nameSuffix}
                      </FieldError>
                      <p className="mt-1 text-xs leading-4 text-[var(--yak-color-text-secondary)]">
                        {prefix && suffix ? `完整表名：${prefix}_${suffix}` : null}
                        {nameState === "checking" && " · 校验中"}
                        {nameState === "available" && " · 可用"}
                        {nameState === "taken" && (
                          <span className="text-[var(--yak-components-badge-danger-text)]">
                            {" "}
                            · 已存在
                          </span>
                        )}
                        {nameState === "error" && " · 名称校验失败"}
                      </p>
                    </>
                  )}
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
            {errors.ownership && (
              <p className="mt-2 text-xs text-[var(--yak-components-badge-danger-text)]">
                {errors.ownership}
              </p>
            )}
          </Section>
          <Section
            title="字段定义"
            extra={
              <span className="flex items-center gap-3 text-xs text-[var(--yak-color-text-secondary)]">
                整表保存，字段顺序即写入顺序
                {rows.length > 0 && !rows.some((row) => row.isPrimaryKey) && (
                  <span className="text-[var(--yak-components-badge-warning-text)]">
                    未设置主键，发布生成的表将不含主键
                  </span>
                )}
              </span>
            }
          >
            {errors.fields && rows.length === 0 && (
              <p className="mb-2 text-xs text-[var(--yak-components-badge-danger-text)]">
                {errors.fields}
              </p>
            )}
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
      {/* Pinned to the bottom of the viewport: a long field table scrolls behind it instead of
          pushing the actions out of reach. */}
      <div className="sticky bottom-0 z-10 flex items-center justify-end gap-2 border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-6 py-3">
        {processBlocked && (
          <span className="mr-auto text-xs text-[var(--yak-color-text-secondary)]">
            请先选择「业务过程」后再保存
          </span>
        )}
        <Button size="small" onClick={requestLeave}>
          取消
        </Button>
        <Button
          size="small"
          variant="primary"
          loading={saving}
          disabled={disabled || suffixMismatch || nameState === "taken" || processBlocked}
          onClick={() => void submit()}
        >
          保存
        </Button>
      </div>
      {confirmLeave && (
        <Dialog open onOpenChange={(open) => !open && setConfirmLeave(false)}>
          <DialogContent className="w-[480px] rounded-xl p-5 shadow-none">
            <DialogTitle className="text-sm font-medium">放弃未保存的改动？</DialogTitle>
            <DialogDescription className="mt-3 text-sm text-[var(--yak-color-text-secondary)]">
              当前页面还有未保存的修改，返回列表后这些修改会丢失。
            </DialogDescription>
            <div className="mt-5 flex justify-end gap-2">
              <Button size="small" onClick={() => setConfirmLeave(false)}>
                继续编辑
              </Button>
              <Button
                size="small"
                variant="danger"
                onClick={() => {
                  setConfirmLeave(false);
                  navigate("/data-modeling/dimension-modeling");
                }}
              >
                放弃并返回
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      )}
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
