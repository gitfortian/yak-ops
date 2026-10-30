import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Switch,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { listDataSourceDatabases, listDataSourceSchemas } from "@/service/datasource";
import {
  checkLayerCode,
  getLayerTargets,
  isDatasourceUnavailable,
  layerFailureField,
  layerFailureMessage,
  saveLayer,
  saveLayerTargets,
  type Layer,
  type LayerDataSource,
  type LayerInput,
} from "@/service/data-modeling/warehouse-planning/layers";
import { requiresSchema } from "../shared/jdbc-engines";

const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";
const CODE = /^[A-Z][A-Z0-9_]{0,31}$/;
const UNBOUND = "__unbound__";
const TARGET_ENVIRONMENTS = ["DEVELOP", "PROD"] as const;
const ENVIRONMENT_LABEL = { DEVELOP: "开发", PROD: "生产" } as const;

/** One editable per-environment binding. */
interface TargetDraft {
  dataSourceId: string;
  database: string;
  schema: string;
}

const EMPTY_TARGET: TargetDraft = { dataSourceId: "", database: "", schema: "" };

interface Props {
  layer: Layer | null;
  dataSources: readonly LayerDataSource[];
  onClose: () => void;
  onSaved: (layer: Layer) => void;
}

export default function LayerDialog({ layer, dataSources, onClose, onSaved }: Props) {
  const id = useId();
  const builtin = layer?.builtin ?? false;
  const [input, setInput] = useState<LayerInput>(() => ({
    cnName: layer?.cnName ?? "",
    enCode: layer?.enCode ?? "",
    dataSourceId: layer?.dataSourceId ?? null,
    description: layer?.description ?? null,
    enabled: layer?.enabled ?? false,
  }));
  const [errors, setErrors] = useState<Partial<Record<keyof LayerInput, string>>>({});
  const [codeTaken, setCodeTaken] = useState(false);
  const [failure, setFailure] = useState("");
  const [conflict, setConflict] = useState(false);
  const [saving, setSaving] = useState(false);
  const [targetDrafts, setTargetDrafts] = useState<Record<string, TargetDraft>>({});
  const [databases, setDatabases] = useState<Record<string, string[]>>({});
  const [schemas, setSchemas] = useState<Record<string, string[]>>({});
  const busy = useRef(false);
  const active = useRef(true);
  const selectedSource = dataSources.find((item) => item.id === input.dataSourceId);
  const selectedUnavailable = isDatasourceUnavailable(selectedSource);
  const sourceLabel = (item: LayerDataSource) =>
    isDatasourceUnavailable(item)
      ? `${item.name}（${item.dbType}）· 连接不可用`
      : `${item.name}（${item.dbType}）`;

  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);

  // The publish dialog resolves its target from these bindings, so an existing layer opens with
  // them loaded; a new layer starts unbound and they are written after the layer itself is saved.
  useEffect(() => {
    if (!layer?.id) return;
    const controller = new AbortController();
    getLayerTargets(layer.id, controller.signal)
      .then((bound) => {
        if (controller.signal.aborted) return;
        setTargetDrafts((previous) => {
          const next = { ...previous };
          for (const target of bound) {
            next[target.environment] = {
              dataSourceId: target.dataSourceId,
              database: target.database,
              schema: target.schema ?? "",
            };
          }
          return next;
        });
      })
      .catch(() => {
        // The dialog stays usable: binding can be retried on the next save.
      });
    return () => controller.abort();
  }, [layer?.id]);

  // Database and schema lists follow the datasource each environment binds.
  useEffect(() => {
    const cleanups: (() => void)[] = [];
    for (const environment of TARGET_ENVIRONMENTS) {
      const draft = targetDrafts[environment];
      if (!draft?.dataSourceId) continue;
      const key = `${environment}:${draft.dataSourceId}`;
      if (databases[key]) continue;
      const controller = new AbortController();
      listDataSourceDatabases(draft.dataSourceId)
        .then((values) => {
          if (!controller.signal.aborted)
            setDatabases((previous) => ({ ...previous, [key]: values }));
        })
        .catch(() => {
          if (!controller.signal.aborted) setDatabases((previous) => ({ ...previous, [key]: [] }));
        });
      cleanups.push(() => controller.abort());
    }
    return () => cleanups.forEach((cleanup) => cleanup());
  }, [targetDrafts, databases]);

  useEffect(() => {
    const cleanups: (() => void)[] = [];
    for (const environment of TARGET_ENVIRONMENTS) {
      const draft = targetDrafts[environment];
      if (
        !draft?.dataSourceId ||
        !draft.database ||
        !requiresSchema(sourceType(draft.dataSourceId))
      )
        continue;
      const key = `${environment}:${draft.dataSourceId}:${draft.database}`;
      if (schemas[key]) continue;
      const controller = new AbortController();
      listDataSourceSchemas(draft.dataSourceId, draft.database)
        .then((values) => {
          if (!controller.signal.aborted)
            setSchemas((previous) => ({ ...previous, [key]: values }));
        })
        .catch(() => {
          if (!controller.signal.aborted) setSchemas((previous) => ({ ...previous, [key]: [] }));
        });
      cleanups.push(() => controller.abort());
    }
    return () => cleanups.forEach((cleanup) => cleanup());
  }, [targetDrafts, schemas]);

  const sourceType = (dataSourceId: string) =>
    dataSources.find((item) => item.id === dataSourceId)?.dbType ?? null;

  const patchTarget = (environment: string, partial: Partial<TargetDraft>) => {
    setTargetDrafts((previous) => {
      const current = previous[environment] ?? EMPTY_TARGET;
      const next = { ...current, ...partial };
      // A schema belongs to one database: switching either clears the stale choice.
      if (partial.dataSourceId !== undefined || partial.database !== undefined) next.schema = "";
      return { ...previous, [environment]: next };
    });
    setFailure("");
  };

  // Probe the Workspace-unique abbreviation so the user learns about a clash before submitting.
  useEffect(() => {
    const enCode = input.enCode.trim().toUpperCase();
    if (builtin || !CODE.test(enCode)) {
      setCodeTaken(false);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      checkLayerCode(enCode, layer?.id, controller.signal)
        .then((result) => {
          if (!controller.signal.aborted) setCodeTaken(!result.available);
        })
        .catch(() => {
          if (!controller.signal.aborted) setCodeTaken(false);
        });
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [builtin, input.enCode, layer?.id]);

  const change = <K extends keyof LayerInput>(key: K, value: LayerInput[K]) => {
    setInput((previous) => ({ ...previous, [key]: value }));
    setErrors((previous) => ({ ...previous, [key]: undefined }));
    if (key === "enCode") setCodeTaken(false);
    setFailure("");
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy.current || conflict) return;
    setFailure("");
    const next: typeof errors = {};
    const cnName = input.cnName.trim();
    const enCode = input.enCode.trim().toUpperCase();
    if (!cnName || cnName.length > 64) next.cnName = "请输入1至64个字符的分层名称";
    if (!builtin && !CODE.test(enCode))
      next.enCode = "须以大写字母开头，仅含大写字母、数字或下划线，最多32位";
    else if (!builtin && codeTaken) next.enCode = "英文缩写已存在";
    if (input.enabled && !input.dataSourceId) next.dataSourceId = "启用前请先绑定物理数据源";
    if (Object.keys(next).length > 0) {
      setErrors(next);
      return;
    }
    busy.current = true;
    setSaving(true);
    try {
      const saved = await saveLayer(layer, {
        cnName,
        enCode,
        dataSourceId: input.dataSourceId,
        description: input.description?.trim() ? input.description.trim() : null,
        enabled: input.enabled,
      });
      // The targets are written after the layer exists: a new layer only has an id now. An
      // environment the user left empty simply unbinds that lane.
      const entries = TARGET_ENVIRONMENTS.map((environment) => {
        const draft = targetDrafts[environment];
        if (!draft?.dataSourceId || !draft.database.trim()) return null;
        const engineSchema = requiresSchema(sourceType(draft.dataSourceId));
        return {
          environment,
          dataSourceId: draft.dataSourceId,
          database: draft.database.trim(),
          schema: engineSchema && draft.schema.trim() ? draft.schema.trim() : null,
        };
      }).filter((entry) => entry !== null);
      await saveLayerTargets(saved.id, entries);
      if (active.current) {
        toast.success("保存成功");
        onSaved(saved);
      }
    } catch (error) {
      if (!active.current) return;
      const field = layerFailureField(error);
      if (field === "revision") setConflict(true);
      else if (field === "enCode")
        setErrors((previous) => ({ ...previous, enCode: "英文缩写已存在" }));
      else if (field === "dataSourceId")
        setErrors((previous) => ({ ...previous, dataSourceId: layerFailureMessage(error) }));
      else setFailure(layerFailureMessage(error));
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
        if (!open && !busy.current) onClose();
      }}
    >
      <DialogContent className="w-[560px] rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {layer ? "编辑分层" : "新建分层"}
        </DialogTitle>
        <form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <Field className={FIELD_CLASS} invalid={Boolean(errors.cnName)}>
            <FieldLabel htmlFor={id + "-cn-name"} required className="pt-1">
              分层中文名
            </FieldLabel>
            <div>
              <Input
                id={id + "-cn-name"}
                size="small"
                variant="outlined"
                value={input.cnName}
                maxLength={64}
                disabled={saving || builtin}
                aria-invalid={Boolean(errors.cnName) || undefined}
                onChange={(event) => change("cnName", event.target.value)}
              />
              <FieldError match={Boolean(errors.cnName)}>{errors.cnName}</FieldError>
            </div>
          </Field>
          <Field className={FIELD_CLASS} invalid={Boolean(errors.enCode) || codeTaken}>
            <FieldLabel htmlFor={id + "-en-code"} required className="pt-1">
              英文缩写
            </FieldLabel>
            <div>
              <Input
                id={id + "-en-code"}
                size="small"
                variant="outlined"
                value={input.enCode}
                maxLength={32}
                disabled={saving || builtin}
                aria-invalid={Boolean(errors.enCode) || codeTaken || undefined}
                onChange={(event) => change("enCode", event.target.value.toUpperCase())}
              />
              <FieldError match={Boolean(errors.enCode) || codeTaken}>
                {errors.enCode ?? (codeTaken ? "英文缩写已存在" : undefined)}
              </FieldError>
            </div>
          </Field>
          <Field className={FIELD_CLASS} invalid={Boolean(errors.dataSourceId)}>
            <FieldLabel htmlFor={id + "-datasource"} className="pt-1">
              物理数据源
            </FieldLabel>
            <div>
              <Select
                value={input.dataSourceId ?? UNBOUND}
                size="small"
                disabled={saving}
                items={[
                  { value: UNBOUND, label: "未绑定" },
                  ...dataSources.map((item) => ({ value: item.id, label: sourceLabel(item) })),
                ]}
                onValueChange={(value) =>
                  change("dataSourceId", value === UNBOUND ? null : (value ?? null))
                }
              >
                <SelectTrigger
                  id={id + "-datasource"}
                  variant="outlined"
                  aria-invalid={Boolean(errors.dataSourceId) || undefined}
                >
                  <SelectValue placeholder="请选择物理数据源" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={UNBOUND}>
                    <SelectItemText>未绑定</SelectItemText>
                  </SelectItem>
                  {dataSources.map((item) => (
                    <SelectItem key={item.id} value={item.id}>
                      <SelectItemText>{sourceLabel(item)}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError match={Boolean(errors.dataSourceId)}>{errors.dataSourceId}</FieldError>
              {selectedUnavailable ? (
                <p className="mt-1 text-xs text-[var(--yak-components-badge-warning-text)]">
                  该数据源连接不可用，启用后发布将无法执行，请先到数据源页测试连接。
                </p>
              ) : (
                <p className="mt-1 text-xs text-[var(--yak-color-text-secondary)]">
                  启用前必须绑定；停用状态可暂不绑定。
                </p>
              )}
            </div>
          </Field>
          <Field className={FIELD_CLASS}>
            <FieldLabel htmlFor={id + "-description"} className="pt-1">
              描述
            </FieldLabel>
            <div>
              <Textarea
                id={id + "-description"}
                rows={3}
                maxLength={500}
                variant="outlined"
                value={input.description ?? ""}
                disabled={saving || builtin}
                onValueChange={(value) => change("description", value)}
              />
            </div>
          </Field>
          <Field className={FIELD_CLASS}>
            <FieldLabel htmlFor={id + "-enabled"} className="pt-1">
              状态
            </FieldLabel>
            <div className="flex items-center gap-2 pt-1">
              <Switch
                id={id + "-enabled"}
                size="small"
                checked={input.enabled}
                disabled={saving}
                onCheckedChange={(checked) => change("enabled", checked)}
              />
              <span className="text-xs text-[var(--yak-color-text-secondary)]">
                {input.enabled ? "启用" : "禁用"}
              </span>
            </div>
          </Field>
          {builtin && (
            <p className="pl-[116px] text-xs text-[var(--yak-color-text-secondary)]">
              默认分层只能调整绑定的物理数据源和启用状态。
            </p>
          )}
          <div className="rounded-lg border border-[var(--yak-components-panel-border)] p-3">
            <p className="text-xs font-medium">发布目标（按环境绑定）</p>
            <p className="mt-0.5 text-xs text-[var(--yak-color-text-secondary)]">
              逻辑模型发布时选择环境即可自动定位目标库，无需每次手动选择。
            </p>
            <div className="mt-2 grid gap-3">
              {TARGET_ENVIRONMENTS.map((environment) => {
                const draft = targetDrafts[environment] ?? EMPTY_TARGET;
                const engineSchema = requiresSchema(sourceType(draft.dataSourceId));
                const databaseKey = `${environment}:${draft.dataSourceId}`;
                const schemaKey = `${databaseKey}:${draft.database}`;
                return (
                  <div
                    key={environment}
                    className="grid gap-2 rounded-md bg-[var(--yak-color-surface-muted)] p-2"
                  >
                    <p className="text-xs font-medium">{ENVIRONMENT_LABEL[environment]}环境</p>
                    <div className="grid gap-2 md:grid-cols-2">
                      <Field>
                        <FieldLabel htmlFor={`${id}-target-${environment}-ds`} className="text-xs">
                          数据源
                        </FieldLabel>
                        <Select
                          value={draft.dataSourceId || UNBOUND}
                          size="small"
                          disabled={saving}
                          items={[
                            { value: UNBOUND, label: "未绑定" },
                            ...dataSources.map((item) => ({
                              value: item.id,
                              label: sourceLabel(item),
                            })),
                          ]}
                          onValueChange={(value) =>
                            patchTarget(environment, {
                              dataSourceId: value === UNBOUND ? "" : (value ?? ""),
                            })
                          }
                        >
                          <SelectTrigger id={`${id}-target-${environment}-ds`} variant="outlined">
                            <SelectValue placeholder="请选择数据源" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value={UNBOUND}>
                              <SelectItemText>未绑定</SelectItemText>
                            </SelectItem>
                            {dataSources.map((item) => (
                              <SelectItem key={item.id} value={item.id}>
                                <SelectItemText>{sourceLabel(item)}</SelectItemText>
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </Field>
                      <Field>
                        <FieldLabel htmlFor={`${id}-target-${environment}-db`} className="text-xs">
                          目标库
                        </FieldLabel>
                        <Select
                          value={draft.database || null}
                          size="small"
                          disabled={
                            saving || !draft.dataSourceId || !databases[databaseKey]?.length
                          }
                          items={(databases[databaseKey] ?? []).map((value) => ({
                            value,
                            label: value,
                          }))}
                          onValueChange={(value) =>
                            patchTarget(environment, { database: value ?? "" })
                          }
                        >
                          <SelectTrigger id={`${id}-target-${environment}-db`} variant="outlined">
                            <SelectValue
                              placeholder={
                                !draft.dataSourceId ? "请先选择数据源" : "请选择目标数据库"
                              }
                            />
                          </SelectTrigger>
                          <SelectContent>
                            {(databases[databaseKey] ?? []).map((value) => (
                              <SelectItem key={value} value={value}>
                                <SelectItemText>{value}</SelectItemText>
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </Field>
                      {engineSchema && (
                        <Field>
                          <FieldLabel
                            htmlFor={`${id}-target-${environment}-schema`}
                            className="text-xs"
                          >
                            Schema
                          </FieldLabel>
                          <Select
                            value={draft.schema || null}
                            size="small"
                            disabled={saving || !draft.database || !schemas[schemaKey]?.length}
                            items={(schemas[schemaKey] ?? []).map((value) => ({
                              value,
                              label: value,
                            }))}
                            onValueChange={(value) =>
                              patchTarget(environment, { schema: value ?? "" })
                            }
                          >
                            <SelectTrigger
                              id={`${id}-target-${environment}-schema`}
                              variant="outlined"
                            >
                              <SelectValue placeholder="请选择 Schema" />
                            </SelectTrigger>
                            <SelectContent>
                              {(schemas[schemaKey] ?? []).map((value) => (
                                <SelectItem key={value} value={value}>
                                  <SelectItemText>{value}</SelectItemText>
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </Field>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          {conflict && <Alert>数据已变更，请关闭弹窗并刷新后重试。</Alert>}
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
              disabled={conflict}
            >
              保存
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
