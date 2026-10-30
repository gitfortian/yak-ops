import {
  Alert,
  Button,
  Checkbox,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
  Table,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import {
  checkModelName,
  classifyModelFailure,
  getDimensions,
  saveModel,
} from "@/service/data-modeling/dimension-modeling";
import { MODEL_TYPE_LABEL } from "@/service/data-modeling/dimension-modeling/types";
import type {
  ConceptDimension,
  DimensionScope,
  ModelFailure,
  ModelInput,
} from "@/service/data-modeling/dimension-modeling/types";
import type { DataSourceCatalogTable } from "@/service/datasource";
import { useCatalogPicker } from "../../use-catalog-picker";
import FieldEditor from "../../logical-models/field-editor";
import {
  MODEL_SUFFIX_RULE,
  isValidModelSuffix,
  toModelFieldInput,
  validateFieldRows,
  type FieldDraft,
} from "../../logical-models/fields";
import { MODEL_LAYER_CODE, layersForType } from "../../logical-models/layer-binding";
import { qualifiedTableName } from "../../logical-models/field-mapping";
import { draftTableCnName, draftTableName } from "../../logical-models/reverse-modeling-draft";
import { defaultReverseLayer, layerBlock } from "./wizard-layers";
import { reverseModelingProvider } from "../../logical-models/reverse-modeling-provider";
import { useModelOptions } from "../../logical-models/state";
import { datamartsOf, domainsOf, processesOf } from "../../shared/planning-tree";

const ENV_LABEL: Record<string, string> = { PROD: "生产", TEST: "测试", DEVELOP: "开发" };

/**
 * Turns a classified save failure into one sentence the page can show.
 *
 * <p>The classifier already separates a naming-rule violation from a version conflict and a field
 * rejection; collapsing all of them into one generic sentence would hide the only actionable part.
 */
function describeModelFailure(failure: ModelFailure): string {
  switch (failure.kind) {
    case "field":
      return failure.message;
    case "conflict":
      return "数据已变更，请刷新页面后重试";
    case "missing":
      return "所选归属对象已不存在，请重新选择";
    case "unknown-type":
      return "模型类型无法识别，请检查分层设置";
    default:
      return failure.message;
  }
}

const pickerSelect = (
  label: string,
  value: string,
  placeholder: string,
  items: { value: string; label: string; disabled?: boolean }[],
  onChange: (value: string) => void,
  disabled = false,
) => (
  <label className="grid gap-1 text-xs">
    <span className="font-medium">{label}</span>
    <Select
      value={value || null}
      size="small"
      disabled={disabled}
      items={items}
      onValueChange={(next) => onChange(next ?? "")}
    >
      <SelectTrigger variant="outlined" aria-label={label}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {items.map((item) => (
          <SelectItem key={item.value} value={item.value} disabled={item.disabled}>
            <SelectItemText>{item.label}</SelectItemText>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  </label>
);

/**
 * Reverse modeling wizard (YOIP-12): pick a physical table, review the generated draft, choose the
 * ownership and save through the ordinary model-creation contract.
 */
export default function ReverseModelingPage() {
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const picker = useCatalogPicker();
  const options = useModelOptions();
  const [step, setStep] = useState(1);
  const [rows, setRows] = useState<FieldDraft[]>([]);
  const [type, setType] = useState<"DWD" | "DIM">("DWD");
  const [scope, setScope] = useState<DimensionScope>("PUBLIC");
  const [layerId, setLayerId] = useState("");
  const [nameSuffix, setNameSuffix] = useState("");
  const [cnName, setCnName] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [domainId, setDomainId] = useState("");
  const [processId, setProcessId] = useState("");
  const [datamartId, setDatamartId] = useState("");
  const [dimensionId, setDimensionId] = useState("");
  const [dimensions, setDimensions] = useState<ConceptDimension[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (layerId) return;
    // The layer supplies the table prefix and is locked 1:1 to the kind, so the default follows the
    // kind. Defaulting to the first enabled layer instead lands a detail table in the 贴源 layer,
    // which saves fine and is then refused by the editor as an illegal model.
    const suggested = defaultReverseLayer(options.layers, type);
    if (suggested) setLayerId(suggested.id);
  }, [layerId, options.layers, type]);

  useEffect(() => {
    if (type !== "DIM" || scope !== "PUBLIC" || !domainId) {
      setDimensions([]);
      setDimensionId("");
      return;
    }
    const controller = new AbortController();
    void getDimensions({ scope: "PUBLIC", scopeId: domainId }, controller.signal)
      .then((values) => setDimensions(values))
      .catch(() => setDimensions([]));
    return () => controller.abort();
  }, [type, scope, domainId]);

  const domains = useMemo(() => domainsOf(options.tree, categoryId), [options.tree, categoryId]);
  const processes = useMemo(() => processesOf(options.tree, domainId), [options.tree, domainId]);
  const datamarts = useMemo(() => datamartsOf(options.tree), [options.tree]);
  const issues = useMemo(() => validateFieldRows(rows), [rows]);
  const selectedLayer = options.layers.find((layer) => layer.id === layerId);
  /** Layers carrying the kind's own code; none means the workspace defines no layer for this kind. */
  const boundLayers = useMemo(() => layersForType(options.layers, type), [options.layers, type]);
  const usableLayer = defaultReverseLayer(options.layers, type);
  /**
   * The table comment is only a starting value for the model name. Flagging it while it is
   * untouched asks the user to confirm, instead of letting a placeholder-looking comment become the
   * model's Chinese name silently.
   */
  const cnNameFromComment =
    Boolean(picker.selectedTable?.remarks?.trim()) &&
    cnName === (picker.selectedTable ? draftTableCnName(picker.selectedTable) : "");
  const layerGapMessage = `${MODEL_TYPE_LABEL[type]}只能建在 ${MODEL_LAYER_CODE[type]} 层，当前工作空间没有启用的该分层，请先到「数仓规划」创建该分层并绑定数据源。`;

  /**
   * Every layer stays listed, because a controlled select drops its value when the option disappears
   * and would blank the field the moment the kind changes. Only the kind's own enabled layer stays
   * pickable, which is the rule the forward editor applies; this page states it per option because
   * this is where the illegal default used to come from.
   */
  const layerItems = options.layers.map((layer) => {
    const block = layerBlock(layer, type, boundLayers.length > 0);
    const reason =
      block === null
        ? ""
        : block === "unenabled"
          ? "未启用"
          : `${MODEL_TYPE_LABEL[type]}限 ${MODEL_LAYER_CODE[type]} 层`;
    return {
      value: layer.id,
      label: `${layer.enCode} · ${layer.cnName}${reason ? `（${reason}）` : ""}`,
      disabled: block !== null,
    };
  });

  const startDraft = () => {
    const table = picker.selectedTable;
    if (!table || picker.columns.length === 0) return;
    const provider = reverseModelingProvider(picker.datasource?.dbType);
    if (!provider) {
      setError("当前数据源类型不支持逆向建模，请选择 MySQL、Oracle 或 PostgreSQL 数据源");
      return;
    }
    setRows(provider.describeDrafts(picker.columns, qualifiedTableName(table)));
    setNameSuffix(draftTableName(table.name, selectedLayer?.enCode));
    setCnName(draftTableCnName(table));
    setError(null);
    setStep(2);
  };

  const changeLayer = (next: string) => {
    setLayerId(next);
    const layer = options.layers.find((item) => item.id === next);
    // The suffix holds only the part the prefix does not already say, so the strip is keyed on the
    // layer code; a layer id is not a prefix and would leave the token in the name field.
    if (picker.selectedTable) {
      setNameSuffix(draftTableName(picker.selectedTable.name, layer?.enCode));
    }
  };

  const changeType = (next: "DWD" | "DIM") => {
    setType(next);
    const nextBound = layersForType(options.layers, next);
    const current = options.layers.find((layer) => layer.id === layerId);
    // A layer the kind may still live in is kept, so switching back does not discard the choice the
    // user made in the list. An unmatched layer is replaced instead of kept: leaving a detail table
    // in the 贴源 layer stores exactly the model the editor flags on open. With no enabled layer for
    // the kind the field goes empty and the reason is stated, so no illegal model can be saved.
    if (current && layerBlock(current, next, nextBound.length > 0) === null) return;
    const suggested = defaultReverseLayer(options.layers, next);
    if (suggested) changeLayer(suggested.id);
    else setLayerId("");
  };

  const ownership = (): string | null => {
    if (!categoryId) return "请选择业务分类";
    if (type === "DWD") {
      if (!domainId) return "请选择数据域";
      if (!processId) return "请选择业务过程";
      return null;
    }
    if (scope === "PUBLIC") {
      if (!domainId) return "请选择数据域";
      if (!dimensionId) return "请选择概念维度";
      return null;
    }
    if (!datamartId) return "请选择数据集市";
    return null;
  };

  const submit = async () => {
    if (!picker.selectedTable) {
      setError("物理表选择已失效，请返回第一步重新选择");
      return;
    }
    const suffix = nameSuffix.trim();
    const problem =
      issues[0]?.message ??
      (usableLayer ? (layerId ? null : "请选择数仓分层") : layerGapMessage) ??
      (!suffix ? "请输入表名后缀" : isValidModelSuffix(suffix) ? null : MODEL_SUFFIX_RULE) ??
      (cnName.trim() ? null : "请输入表中文名") ??
      ownership();
    if (problem) {
      setError(problem);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const available = await checkModelName(layerId, suffix);
      if (!available) {
        setError("表名在当前工作空间已存在");
        return;
      }
      const input: ModelInput = {
        type,
        layerId,
        categoryId,
        processId: type === "DWD" ? processId || null : null,
        domainId:
          type === "DWD" || (type === "DIM" && scope === "PUBLIC") ? domainId || null : null,
        datamartId: type === "DIM" && scope === "APPLICATION" ? datamartId || null : null,
        dimensionId: type === "DIM" && scope === "PUBLIC" ? dimensionId || null : null,
        nameSuffix: suffix,
        cnName: cnName.trim(),
        storagePolicy: "DAILY_INCREMENTAL",
        lifecycleDays: 30,
        ownerId: currentUser?.id ?? "",
        description: null,
        fields: rows.map(toModelFieldInput),
      };
      const saved = await saveModel(null, input);
      toast.success("创建成功");
      navigate(`/data-modeling/logical-models/${saved.id}`);
    } catch (error) {
      setError(describeModelFailure(classifyModelFailure(error)));
    } finally {
      setSaving(false);
    }
  };

  const tableColumns: TableColumns<DataSourceCatalogTable> = [
    { title: "表名", dataIndex: "name", width: 280, ellipsis: true },
    { title: "类型", dataIndex: "type", width: 90 },
    { title: "备注", dataIndex: "remarks", ellipsis: true },
    {
      title: "操作",
      key: "action",
      width: 90,
      align: "center",
      render: (_, row) => (
        <Button
          size="small"
          variant="ghost"
          onClick={() => picker.selectTable(row)}
          disabled={picker.selectedTable?.name === row.name}
        >
          {picker.selectedTable?.name === row.name ? "已选择" : "选择"}
        </Button>
      ),
    },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-base font-medium">逆向建模</h1>
        <Button
          size="small"
          className="ml-auto"
          onClick={() => navigate("/data-modeling/dimension-modeling")}
        >
          返回维度建模
        </Button>
      </div>

      <ol className="flex flex-wrap items-center gap-2 text-xs text-[var(--yak-color-text-secondary)]">
        <li className={step === 1 ? "font-medium text-[var(--yak-components-panel-text)]" : ""}>
          1 选择物理表
        </li>
        <li>·</li>
        <li className={step === 2 ? "font-medium text-[var(--yak-components-panel-text)]" : ""}>
          2 核对字段
        </li>
        <li>·</li>
        <li className={step === 3 ? "font-medium text-[var(--yak-components-panel-text)]" : ""}>
          3 归属与保存
        </li>
      </ol>

      {error && (
        <Alert>
          <span>{error}</span>
        </Alert>
      )}

      {options.status === "ready" && !usableLayer && (
        <Alert>
          <span>{layerGapMessage}</span>
        </Alert>
      )}

      {step === 1 && (
        <section className="flex min-w-0 flex-col gap-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            {pickerSelect(
              "数据源",
              picker.datasourceId,
              picker.loading.datasources ? "数据源加载中" : "请选择数据源",
              picker.datasources.map((item) => ({
                value: item.id ?? "",
                label: `${item.name ?? item.id}（${ENV_LABEL[item.environment ?? ""] ?? item.environment ?? "未知"}）`,
              })),
              picker.selectDatasource,
              picker.loading.datasources,
            )}
            {picker.databases.length > 0 &&
              pickerSelect(
                "数据库",
                picker.database,
                "请选择数据库",
                picker.databases.map((value) => ({ value, label: value })),
                picker.selectDatabase,
              )}
            {picker.schemas.length > 0 &&
              pickerSelect(
                "Schema",
                picker.schema,
                "请选择 Schema",
                picker.schemas.map((value) => ({ value, label: value })),
                picker.selectSchema,
              )}
          </div>
          {picker.nonProduction && (
            <div className="flex items-start gap-2 rounded-lg border border-[var(--yak-color-border-danger-subtle)] bg-[var(--yak-color-surface-danger-subtle)] px-3 py-2 text-xs text-[var(--yak-color-text-danger)]">
              <Checkbox
                aria-label="确认非生产环境"
                checked={picker.envConfirmed}
                onCheckedChange={(checked) => picker.setEnvConfirmed(checked)}
              />
              <span>
                即将从「
                {ENV_LABEL[picker.datasource?.environment ?? ""] ?? picker.datasource?.environment}
                」环境读取表结构，请确认连接的是目标库；环境只作提示，不做拦截。
              </span>
            </div>
          )}
          {picker.failureMessages.length > 0 && (
            <Alert>
              <div>
                {picker.failureMessages.map((message) => (
                  <div key={message}>{message}</div>
                ))}
              </div>
              <Button size="small" onClick={picker.retry}>
                重试
              </Button>
            </Alert>
          )}
          <div className="flex items-center gap-2">
            <Input
              size="small"
              variant="outlined"
              value={picker.tableKeyword}
              maxLength={128}
              disabled={!picker.datasourceId || picker.blocked}
              aria-label="搜索表名"
              placeholder="表名关键字"
              onChange={(event) => picker.setTableKeyword(event.target.value)}
            />
            <Button
              size="small"
              disabled={!picker.datasourceId || picker.blocked}
              onClick={picker.searchTables}
            >
              查询
            </Button>
          </div>
          {picker.loading.tables ? (
            <div className="flex h-40 items-center justify-center">
              <Spinner />
            </div>
          ) : (
            <div className="max-h-[420px] overflow-auto">
              <Table<DataSourceCatalogTable>
                rowKey="name"
                columns={tableColumns}
                dataSource={picker.tables}
                size="small"
                bordered={false}
                emptyText={
                  picker.blocked
                    ? "请先确认非生产环境"
                    : picker.failures.tables
                      ? "表列表读取失败，请重试"
                      : picker.tablesLoaded
                        ? "未查询到表或视图"
                        : "请先选择数据源与库表并查询"
                }
                pagination={false}
                scroll={{ x: 620 }}
              />
            </div>
          )}
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-xs text-[var(--yak-color-text-secondary)]">
              {picker.selectedTable
                ? picker.loading.columns
                  ? `正在读取 ${picker.selectedTable.name} 的字段`
                  : `已选 ${picker.selectedTable.name}，读取到 ${picker.columns.length} 个字段`
                : "先在表中选定一张物理表"}
            </span>
            <Button
              size="small"
              variant="primary"
              disabled={picker.loading.columns || picker.columns.length === 0}
              onClick={startDraft}
            >
              生成草稿
            </Button>
          </div>
        </section>
      )}

      {step === 2 && (
        <section className="flex min-w-0 flex-col gap-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            {pickerSelect(
              "数仓分层",
              layerId,
              usableLayer ? "请选择数仓分层" : `无可用分层（${MODEL_LAYER_CODE[type]}）`,
              layerItems,
              changeLayer,
              options.status !== "ready",
            )}
            <label className="grid gap-1 text-xs">
              <span className="font-medium">表名后缀</span>
              <Input
                size="small"
                variant="outlined"
                value={nameSuffix}
                maxLength={41}
                aria-label="表名后缀"
                onChange={(event) => setNameSuffix(event.target.value)}
              />
            </label>
            <label className="grid gap-1 text-xs">
              <span className="font-medium">表中文名</span>
              <Input
                size="small"
                variant="outlined"
                value={cnName}
                maxLength={128}
                aria-label="表中文名"
                onChange={(event) => setCnName(event.target.value)}
              />
              {cnNameFromComment && (
                <span className="text-[var(--yak-color-text-secondary)]">
                  继承自源表注释，请确认
                </span>
              )}
            </label>
          </div>
          <FieldEditor
            rows={rows}
            onChange={setRows}
            issues={issues}
            hideImportActions
            disabled={saving}
          />
          <div className="flex gap-2">
            <Button size="small" onClick={() => setStep(1)}>
              上一步
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={rows.length === 0}
              onClick={() => setStep(3)}
            >
              下一步
            </Button>
          </div>
        </section>
      )}

      {step === 3 && (
        <section className="flex min-w-0 flex-col gap-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            {pickerSelect(
              "模型类型",
              type,
              "请选择模型类型",
              [
                { value: "DWD", label: "明细表 DWD" },
                { value: "DIM", label: "维度表 DIM" },
              ],
              (value) => changeType(value === "DIM" ? "DIM" : "DWD"),
            )}
            {type === "DIM" &&
              pickerSelect(
                "维度归属",
                scope,
                "请选择归属",
                [
                  { value: "PUBLIC", label: "公共层（数据域 + 概念维度）" },
                  { value: "APPLICATION", label: "应用层（数据集市）" },
                ],
                (value) => {
                  setScope(value === "APPLICATION" ? "APPLICATION" : "PUBLIC");
                  setDomainId("");
                  setDimensionId("");
                  setDatamartId("");
                },
              )}
            {pickerSelect(
              "业务分类",
              categoryId,
              "请选择业务分类",
              options.tree.categories.map((node) => ({ value: node.id, label: node.name })),
              (value) => {
                setCategoryId(value);
                setDomainId("");
                setProcessId("");
              },
              options.status !== "ready",
            )}
            {(type === "DWD" || scope === "PUBLIC") &&
              pickerSelect(
                "数据域",
                domainId,
                "请选择数据域",
                domains.map((domain) => ({ value: domain.id, label: domain.name })),
                (value) => {
                  setDomainId(value);
                  setProcessId("");
                  setDimensionId("");
                },
                !categoryId,
              )}
            {type === "DWD" &&
              pickerSelect(
                "业务过程",
                processId,
                "请选择业务过程",
                processes.map((process) => ({ value: process.id, label: process.name })),
                setProcessId,
                !domainId,
              )}
            {type === "DIM" &&
              scope === "PUBLIC" &&
              pickerSelect(
                "概念维度",
                dimensionId,
                "请选择概念维度",
                dimensions.map((dimension) => ({ value: dimension.id, label: dimension.name })),
                setDimensionId,
                !domainId,
              )}
            {type === "DIM" &&
              scope === "APPLICATION" &&
              pickerSelect(
                "数据集市",
                datamartId,
                "请选择数据集市",
                datamarts.map((datamart) => ({ value: datamart.id, label: datamart.name })),
                setDatamartId,
                options.status !== "ready",
              )}
          </div>
          <p className="text-xs text-[var(--yak-color-text-secondary)]">
            仅保存模型，不发布物理表。
          </p>
          <div className="flex gap-2">
            <Button size="small" onClick={() => setStep(2)}>
              上一步
            </Button>
            <Button size="small" variant="primary" disabled={saving} onClick={() => void submit()}>
              {saving ? "保存中" : "保存并打开"}
            </Button>
          </div>
        </section>
      )}
    </div>
  );
}
