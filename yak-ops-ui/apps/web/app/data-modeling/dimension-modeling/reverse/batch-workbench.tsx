import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Drawer,
  DrawerContent,
  DrawerTitle,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
  Switch,
  Table,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { useAuth } from "@/hooks/use-auth";
import {
  checkModelName,
  classifyModelFailure,
  saveModel,
} from "@/service/data-modeling/dimension-modeling";
import { MODEL_TYPE_LABEL } from "@/service/data-modeling/dimension-modeling/types";
import type { ModelInput } from "@/service/data-modeling/dimension-modeling/types";
import {
  listDataSourceColumns,
  type DataSourceCatalogColumn,
  type DataSourceCatalogTable,
} from "@/service/datasource";
import { getStandards } from "@/service/data-modeling/data-standards";
import type { StandardField } from "@/service/data-modeling/data-standards/types";
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
import { domainsOf, processesOf } from "../../shared/planning-tree";

const ENV_LABEL: Record<string, string> = { PROD: "生产", TEST: "测试", DEVELOP: "开发" };

/** 并发读取字段结构的上限；逐表顺序读取在几十张表时会明显变慢。 */
const COLUMN_FETCH_CONCURRENCY = 3;

/** 标准字典参与自动匹配的规模上限；再多的标准走编辑器手动绑定。 */
const STANDARD_MATCH_LIMIT = 200;

type Conflict = "checking" | "available" | "exists";
type RowStatus = "pending" | "created" | "failed";

interface BatchEntry {
  key: string;
  table: DataSourceCatalogTable;
  rows: FieldDraft[];
  suffix: string;
  cnName: string;
  include: boolean;
  conflict: Conflict;
  status: RowStatus;
  failure: string | null;
  loadingColumns: boolean;
  columnFailed: boolean;
  matchedStandards: number;
}

/** 归一化编码：小写并去下划线，用于与字段标准编码做确定性匹配。 */
const normalizeCode = (value: string) => value.toLowerCase().replace(/_/g, "");

/**
 * 批量逆向工作台：一次勾选多张物理表，统一生成映射草稿、核对表名冲突并批量创建模型。
 *
 * <p>创建走既有的单模型契约逐张提交——契约不变，变化的是不再一张表重复一次三步向导。
 * 字段编码命中字段标准时自动绑定标准并继承类型与约束，绑定数在行内可见。
 */
export default function BatchReversePage() {
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const picker = useCatalogPicker();
  const options = useModelOptions();

  const [entries, setEntries] = useState<BatchEntry[]>([]);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [type, setType] = useState<"DWD" | "DIM">("DWD");
  const [layerId, setLayerId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [domainId, setDomainId] = useState("");
  const [processId, setProcessId] = useState("");
  const [autoMatch, setAutoMatch] = useState(true);
  const [standards, setStandards] = useState<StandardField[]>([]);
  const [editing, setEditing] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const [error, setError] = useState<string | null>(null);
  const conflictToken = useRef(0);

  const domains = useMemo(() => domainsOf(options.tree, categoryId), [options.tree, categoryId]);
  const processes = useMemo(() => processesOf(options.tree, domainId), [options.tree, domainId]);
  const boundLayers = useMemo(() => layersForType(options.layers, type), [options.layers, type]);
  const layerItems = useMemo(
    () =>
      options.layers.map((layer): { value: string; label: string; disabled?: boolean } => {
        const block = layerBlock(layer, type, boundLayers.length > 0);
        const reason =
          block === null
            ? ""
            : block === "unenabled"
              ? "（未启用）"
              : `（${MODEL_TYPE_LABEL[type]}限 ${MODEL_LAYER_CODE[type]} 层）`;
        return {
          value: layer.id,
          label: `${layer.enCode} · ${layer.cnName}${reason}`,
          disabled: block !== null,
        };
      }),
    [options.layers, type, boundLayers.length],
  );
  const usableLayer = defaultReverseLayer(options.layers, type);
  const layerGapMessage = `${MODEL_TYPE_LABEL[type]}只能建在 ${MODEL_LAYER_CODE[type]} 层，当前工作空间没有启用的该分层，请先到「数仓规划」创建该分层并绑定数据源。`;

  // 默认分层跟随模型类型，与单表向导同一规则。
  useEffect(() => {
    if (layerId) return;
    const suggested = defaultReverseLayer(options.layers, type);
    if (suggested) setLayerId(suggested.id);
  }, [layerId, options.layers, type]);

  // 标准字典载入一次，用于编码自动匹配。
  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([
      getStandards({ scope: "ALL", keyword: "", pageNo: 1, pageSize: 100 }, controller.signal),
      getStandards({ scope: "ALL", keyword: "", pageNo: 2, pageSize: 100 }, controller.signal),
    ])
      .then(([first, second]) => {
        if (controller.signal.aborted) return;
        setStandards([...first.bizData, ...second.bizData].slice(0, STANDARD_MATCH_LIMIT));
      })
      .catch(() => {
        if (!controller.signal.aborted) setStandards([]);
      });
    return () => controller.abort();
  }, []);

  const standardsByCode = useMemo(() => {
    const map = new Map<string, StandardField>();
    standards.forEach((standard) => {
      const key = normalizeCode(standard.enCode);
      if (!map.has(key)) map.set(key, standard);
    });
    return map;
  }, [standards]);

  /** 把字段编码对到字段标准；命中即绑定标准并继承其类型与约束。 */
  const applyStandardMatches = useCallback(
    (rows: FieldDraft[]): { rows: FieldDraft[]; matched: number } => {
      if (!autoMatch) return { rows, matched: 0 };
      let matched = 0;
      const bound = rows.map((row) => {
        if (row.standardId) return row;
        const standard = standardsByCode.get(normalizeCode(row.enCode));
        if (!standard) return row;
        matched += 1;
        return {
          ...row,
          standardId: standard.id,
          standardEnCode: standard.enCode,
          dataType: standard.dataType,
          lengthPrecision: standard.lengthPrecision ?? row.lengthPrecision,
          isNotNull: standard.isNotNull || row.isNotNull,
          lookupId: standard.lookupId ?? row.lookupId,
          lookupEnCode: standard.lookupEnCode ?? row.lookupEnCode,
          measureUnitId: standard.measureUnitId ?? row.measureUnitId,
          measureUnitEnCode: standard.measureUnitEnCode ?? row.measureUnitEnCode,
          importNote: [row.importNote, `已自动绑定标准 ${standard.enCode}`]
            .filter((note): note is string => Boolean(note))
            .join("；"),
        };
      });
      return { rows: bound, matched };
    },
    [autoMatch, standardsByCode],
  );

  /** 读取一张表的字段结构并生成草稿；列读取失败时该行进入失败态。 */
  const buildEntry = useCallback(
    async (table: DataSourceCatalogTable): Promise<BatchEntry> => {
      const base: BatchEntry = {
        key: table.name,
        table,
        rows: [],
        suffix: "",
        cnName: "",
        include: true,
        conflict: "checking",
        status: "pending",
        failure: null,
        loadingColumns: true,
        columnFailed: false,
        matchedStandards: 0,
      };
      try {
        const columns: DataSourceCatalogColumn[] = await listDataSourceColumns(
          picker.datasourceId,
          {
            database: table.database ?? (picker.database || undefined),
            schema: table.schema ?? (picker.schema || undefined),
            table: table.name,
          },
        );
        const provider = reverseModelingProvider(picker.datasource?.dbType);
        if (!provider) {
          return {
            ...base,
            loadingColumns: false,
            columnFailed: true,
            failure: "该数据源类型不支持逆向建模",
          };
        }
        const rows = provider.describeDrafts(columns, qualifiedTableName(table));
        const { rows: bound, matched } = applyStandardMatches(rows);
        const layer = options.layers.find((item) => item.id === layerId);
        return {
          ...base,
          rows: bound,
          suffix: draftTableName(table.name, layer?.enCode),
          cnName: draftTableCnName(table),
          loadingColumns: false,
          matchedStandards: matched,
        };
      } catch {
        return { ...base, loadingColumns: false, columnFailed: true, failure: "字段结构读取失败" };
      }
    },
    [
      applyStandardMatches,
      layerId,
      options.layers,
      picker.database,
      picker.datasource?.dbType,
      picker.datasourceId,
      picker.schema,
    ],
  );

  // 勾选集合与条目对齐：新勾选的表逐批生成草稿，取消勾选仅摘除包含标记。
  const selectedTables = picker.tables.filter((table) => selectedKeys.has(table.name));
  useEffect(() => {
    const known = new Set(entries.map((entry) => entry.key));
    const missing = selectedTables.filter((table) => !known.has(table.name));
    if (missing.length === 0) return;
    let cancelled = false;
    void (async () => {
      for (let index = 0; index < missing.length; index += COLUMN_FETCH_CONCURRENCY) {
        if (cancelled) return;
        const group = missing.slice(index, index + COLUMN_FETCH_CONCURRENCY);
        const builtGroup = await Promise.all(group.map((table) => buildEntry(table)));
        if (cancelled) return;
        setEntries((current) => {
          const keys = new Set(current.map((entry) => entry.key));
          return [...current, ...builtGroup.filter((entry) => !keys.has(entry.key))];
        });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedTables, entries.length, buildEntry]);

  // 冲突检查：分层与后缀就绪后整批防抖核对一次。
  const suffixSignature = entries
    .map((entry) => `${entry.key}:${entry.suffix}:${entry.conflict}`)
    .join("|");
  useEffect(() => {
    const pending = entries.filter(
      (entry) =>
        entry.include &&
        !entry.loadingColumns &&
        !entry.columnFailed &&
        entry.status === "pending" &&
        isValidModelSuffix(entry.suffix.trim()),
    );
    if (!layerId || pending.length === 0) return;
    const token = ++conflictToken.current;
    const timer = window.setTimeout(() => {
      pending.forEach((entry) => {
        void checkModelName(layerId, entry.suffix.trim())
          .then((available) => {
            if (conflictToken.current !== token) return;
            setEntries((current) =>
              current.map((candidate) =>
                candidate.key === entry.key
                  ? { ...candidate, conflict: available ? "available" : "exists" }
                  : candidate,
              ),
            );
          })
          .catch(() => {
            if (conflictToken.current !== token) return;
            setEntries((current) =>
              current.map((candidate) =>
                candidate.key === entry.key ? { ...candidate, conflict: "checking" } : candidate,
              ),
            );
          });
      });
    }, 400);
    return () => window.clearTimeout(timer);
    // suffixSignature 已覆盖全部触发因素。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layerId, suffixSignature]);

  const patch = (key: string, changes: Partial<BatchEntry>) =>
    setEntries((current) =>
      current.map((entry) => (entry.key === key ? { ...entry, ...changes } : entry)),
    );

  const ownership = (): string | null => {
    if (!categoryId) return "请选择业务分类";
    if (type === "DWD") {
      if (!domainId) return "请选择数据域";
      if (!processId) return "请选择业务过程";
      return null;
    }
    return null;
  };

  const included = entries.filter((entry) => entry.include && entry.status === "pending");
  const readyToCreate = included.filter(
    (entry) => !entry.loadingColumns && !entry.columnFailed && entry.conflict !== "checking",
  );

  const createAll = async () => {
    const globalProblem =
      (usableLayer ? (layerId ? null : "请选择数仓分层") : layerGapMessage) ?? ownership();
    if (globalProblem) {
      setError(globalProblem);
      return;
    }
    setError(null);
    setRunning(true);
    setProgress({ done: 0, total: readyToCreate.length });
    let failures = 0;
    for (const entry of readyToCreate) {
      const rowProblem =
        validateFieldRows(entry.rows)[0]?.message ??
        (!entry.suffix.trim() ? "请输入表名后缀" : null) ??
        (!isValidModelSuffix(entry.suffix.trim()) ? MODEL_SUFFIX_RULE : null) ??
        (!entry.cnName.trim() ? "请输入表中文名" : null);
      if (rowProblem) {
        patch(entry.key, { status: "failed", failure: rowProblem });
        failures += 1;
        setProgress((previous) => ({ ...previous, done: previous.done + 1 }));
        continue;
      }
      try {
        const available = await checkModelName(layerId, entry.suffix.trim());
        if (!available) {
          patch(entry.key, {
            status: "failed",
            failure: "表名在当前工作空间已存在",
            conflict: "exists",
          });
          failures += 1;
          setProgress((previous) => ({ ...previous, done: previous.done + 1 }));
          continue;
        }
        const input: ModelInput = {
          type,
          layerId,
          categoryId,
          processId: type === "DWD" ? processId || null : null,
          domainId: type === "DWD" ? domainId || null : null,
          datamartId: null,
          dimensionId: null,
          nameSuffix: entry.suffix.trim(),
          cnName: entry.cnName.trim(),
          storagePolicy: "DAILY_INCREMENTAL",
          lifecycleDays: 30,
          ownerId: currentUser?.id ?? "",
          description: null,
          physicalProperties: null,
          fields: entry.rows.map(toModelFieldInput),
        };
        await saveModel(null, input);
        patch(entry.key, { status: "created", include: false });
      } catch (cause) {
        const failure = classifyModelFailure(cause);
        patch(entry.key, {
          status: "failed",
          failure: "message" in failure ? failure.message : "创建失败，请稍后重试",
        });
        failures += 1;
      }
      setProgress((previous) => ({ ...previous, done: previous.done + 1 }));
    }
    setRunning(false);
    if (failures === 0) toast.success(`已创建 ${readyToCreate.length} 个模型`);
    else toast.warning(`创建完成：${readyToCreate.length - failures} 成功，${failures} 失败`);
  };

  const editingEntry = entries.find((entry) => entry.key === editing);

  const toggleTable = (table: DataSourceCatalogTable, checked: boolean) => {
    setSelectedKeys((current) => {
      const next = new Set(current);
      if (checked) next.add(table.name);
      else next.delete(table.name);
      return next;
    });
  };

  const allChecked =
    picker.tables.length > 0 && picker.tables.every((table) => selectedKeys.has(table.name));

  const tableColumns: TableColumns<DataSourceCatalogTable> = [
    {
      title: "",
      key: "check",
      width: 44,
      render: (_, row) => (
        <Checkbox
          aria-label={`选择 ${row.name}`}
          checked={selectedKeys.has(row.name)}
          onCheckedChange={(checked) => toggleTable(row, checked === true)}
        />
      ),
    },
    { title: "表名", dataIndex: "name", width: 260, ellipsis: true },
    { title: "类型", dataIndex: "type", width: 90 },
    { title: "备注", dataIndex: "remarks", ellipsis: true },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex gap-1 rounded-lg bg-[var(--yak-color-surface-muted)] p-1">
          {[
            { key: "batch", label: "批量接入" },
            { key: "single", label: "单表精修" },
          ].map((item) => (
            <button
              key={item.key}
              type="button"
              aria-pressed={item.key === "batch"}
              onClick={() => {
                if (item.key === "single") navigate("/data-modeling/dimension-modeling/reverse");
              }}
              className={[
                "rounded-md px-3 py-1.5 text-xs transition-colors duration-150",
                item.key === "batch"
                  ? "bg-[var(--yak-components-panel-bg)] font-medium text-[var(--yak-color-text-primary)] shadow-[0_1px_2px_0_rgb(16_24_40/6%)]"
                  : "text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-text-primary)]",
              ].join(" ")}
            >
              {item.label}
            </button>
          ))}
        </div>
        <h1 className="text-base font-medium">逆向建模 · 批量接入</h1>
        <Button
          size="small"
          className="ml-auto"
          onClick={() => navigate("/data-modeling/dimension-modeling")}
        >
          返回维度建模
        </Button>
      </div>

      {error && <Alert>{error}</Alert>}
      {options.status === "ready" && !usableLayer && (
        <Alert>
          <span>{layerGapMessage}</span>
        </Alert>
      )}

      {/* 选源 */}
      <section className="rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4 shadow-[var(--yak-datamap-card-shadow)]">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <label className="grid gap-1 text-xs">
            <span className="font-medium">数据源</span>
            <Select
              value={picker.datasourceId || null}
              size="small"
              disabled={picker.loading.datasources}
              items={picker.datasources.map((item) => ({
                value: item.id ?? "",
                label: `${item.name ?? item.id}（${ENV_LABEL[item.environment ?? ""] ?? item.environment ?? "未知"}）`,
              }))}
              onValueChange={(value) => {
                setSelectedKeys(new Set());
                picker.selectDatasource(value ?? "");
              }}
            >
              <SelectTrigger variant="outlined" aria-label="数据源">
                <SelectValue
                  placeholder={picker.loading.datasources ? "数据源加载中" : "请选择数据源"}
                />
              </SelectTrigger>
              <SelectContent>
                {picker.datasources.map((item) => (
                  <SelectItem key={item.id ?? ""} value={item.id ?? ""}>
                    <SelectItemText>
                      {item.name ?? item.id}（
                      {ENV_LABEL[item.environment ?? ""] ?? item.environment ?? "未知"}）
                    </SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
          {picker.databases.length > 0 && (
            <label className="grid gap-1 text-xs">
              <span className="font-medium">数据库</span>
              <Select
                value={picker.database || null}
                size="small"
                items={picker.databases.map((value) => ({ value, label: value }))}
                onValueChange={(value) => {
                  setSelectedKeys(new Set());
                  picker.selectDatabase(value ?? "");
                }}
              >
                <SelectTrigger variant="outlined" aria-label="数据库">
                  <SelectValue placeholder="请选择数据库" />
                </SelectTrigger>
                <SelectContent>
                  {picker.databases.map((value) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{value}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          )}
          {picker.schemas.length > 0 && (
            <label className="grid gap-1 text-xs">
              <span className="font-medium">Schema</span>
              <Select
                value={picker.schema || null}
                size="small"
                items={picker.schemas.map((value) => ({ value, label: value }))}
                onValueChange={(value) => {
                  setSelectedKeys(new Set());
                  picker.selectSchema(value ?? "");
                }}
              >
                <SelectTrigger variant="outlined" aria-label="Schema">
                  <SelectValue placeholder="请选择 Schema" />
                </SelectTrigger>
                <SelectContent>
                  {picker.schemas.map((value) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{value}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          )}
          <div className="flex items-end gap-2">
            <Input
              size="small"
              variant="outlined"
              value={picker.tableKeyword}
              maxLength={128}
              disabled={!picker.datasourceId || picker.blocked}
              aria-label="搜索表名"
              placeholder="表名关键字"
              onChange={(event) => picker.setTableKeyword(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") picker.searchTables();
              }}
            />
            <Button
              size="small"
              disabled={!picker.datasourceId || picker.blocked}
              onClick={picker.searchTables}
            >
              查询
            </Button>
          </div>
        </div>

        {picker.nonProduction && (
          <div className="mt-3 flex items-start gap-2 rounded-lg border border-[var(--yak-color-border-danger-subtle)] bg-[var(--yak-color-surface-danger-subtle)] px-3 py-2 text-xs text-[var(--yak-color-text-danger)]">
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
          <Alert className="mt-3">
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

        <div className="mt-3">
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
                    : "请先选择数据源与库并查询"
            }
            pagination={false}
            scroll={{ x: 620 }}
          />
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-[var(--yak-components-muted-text)]">
          <button
            type="button"
            className="cursor-pointer hover:text-[var(--yak-color-primary)]"
            disabled={picker.tables.length === 0}
            onClick={() =>
              setSelectedKeys(
                allChecked ? new Set() : new Set(picker.tables.map((table) => table.name)),
              )
            }
          >
            {allChecked ? "取消全选" : "全选当前列表"}
          </button>
          <span>
            已选 {selectedTables.length} 张 · 已生成映射 {entries.length} 张
          </span>
        </div>
      </section>

      {/* 全局归属与选项 */}
      <div className="rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4 shadow-[var(--yak-datamap-card-shadow)]">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
          <label className="grid gap-1 text-xs">
            <span className="font-medium">模型类型</span>
            <Select
              value={type}
              size="small"
              items={[
                { value: "DWD", label: "明细表 DWD" },
                { value: "DIM", label: "维度表 DIM" },
              ]}
              onValueChange={(value) => {
                setType(value === "DIM" ? "DIM" : "DWD");
                setLayerId("");
              }}
            >
              <SelectTrigger variant="outlined" aria-label="模型类型">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="DWD">
                  <SelectItemText>明细表 DWD</SelectItemText>
                </SelectItem>
                <SelectItem value="DIM">
                  <SelectItemText>维度表 DIM</SelectItemText>
                </SelectItem>
              </SelectContent>
            </Select>
          </label>
          <label className="grid gap-1 text-xs">
            <span className="font-medium">数仓分层</span>
            <Select
              value={layerId || null}
              size="small"
              disabled={options.status !== "ready"}
              items={layerItems}
              onValueChange={(value) => setLayerId(value ?? "")}
            >
              <SelectTrigger variant="outlined" aria-label="数仓分层">
                <SelectValue placeholder={usableLayer ? "请选择数仓分层" : "无可用分层"} />
              </SelectTrigger>
              <SelectContent>
                {layerItems.map((item) => (
                  <SelectItem key={item.value} value={item.value} disabled={item.disabled}>
                    <SelectItemText>{item.label}</SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
          <label className="grid gap-1 text-xs">
            <span className="font-medium">业务分类</span>
            <Select
              value={categoryId || null}
              size="small"
              disabled={options.status !== "ready"}
              items={options.tree.categories.map((node) => ({ value: node.id, label: node.name }))}
              onValueChange={(value) => {
                setCategoryId(value ?? "");
                setDomainId("");
                setProcessId("");
              }}
            >
              <SelectTrigger variant="outlined" aria-label="业务分类">
                <SelectValue placeholder="请选择业务分类" />
              </SelectTrigger>
              <SelectContent>
                {options.tree.categories.map((node) => (
                  <SelectItem key={node.id} value={node.id}>
                    <SelectItemText>{node.name}</SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
          <label className="grid gap-1 text-xs">
            <span className="font-medium">数据域</span>
            <Select
              value={domainId || null}
              size="small"
              disabled={!categoryId}
              items={domains.map((domain) => ({ value: domain.id, label: domain.name }))}
              onValueChange={(value) => {
                setDomainId(value ?? "");
                setProcessId("");
              }}
            >
              <SelectTrigger variant="outlined" aria-label="数据域">
                <SelectValue placeholder="请选择数据域" />
              </SelectTrigger>
              <SelectContent>
                {domains.map((domain) => (
                  <SelectItem key={domain.id} value={domain.id}>
                    <SelectItemText>{domain.name}</SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
        </div>
        <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-4">
          {type === "DWD" && (
            <label className="grid gap-1 text-xs">
              <span className="font-medium">业务过程</span>
              <Select
                value={processId || null}
                size="small"
                disabled={!domainId}
                items={processes.map((process) => ({ value: process.id, label: process.name }))}
                onValueChange={(value) => setProcessId(value ?? "")}
              >
                <SelectTrigger variant="outlined" aria-label="业务过程">
                  <SelectValue placeholder="请选择业务过程" />
                </SelectTrigger>
                <SelectContent>
                  {processes.map((process) => (
                    <SelectItem key={process.id} value={process.id}>
                      <SelectItemText>{process.name}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          )}
          <label className="col-span-1 flex items-center gap-2 text-xs text-[var(--yak-components-panel-text)] md:col-span-3 md:pt-4">
            <Switch checked={autoMatch} onCheckedChange={(checked) => setAutoMatch(checked)} />
            按字段编码自动绑定数据标准（命中即继承类型与约束）
            {standards.length > 0 && (
              <span className="text-[var(--yak-components-muted-text)]">
                已载入 {standards.length} 项标准
              </span>
            )}
          </label>
        </div>
      </div>

      {/* 批量摘要表 */}
      <div className="overflow-hidden rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] shadow-[var(--yak-datamap-card-shadow)]">
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)] text-left text-xs text-[var(--yak-components-muted-text)]">
              <th className="w-14 px-3 py-2.5 font-medium">包含</th>
              <th className="px-3 py-2.5 font-medium">物理表</th>
              <th className="w-16 px-3 py-2.5 text-right font-medium">字段</th>
              <th className="w-[180px] px-3 py-2.5 font-medium">表名后缀</th>
              <th className="w-[200px] px-3 py-2.5 font-medium">表中文名</th>
              <th className="w-24 px-3 py-2.5 font-medium">表名冲突</th>
              <th className="w-28 px-3 py-2.5 font-medium">标准匹配</th>
              <th className="w-24 px-3 py-2.5 font-medium">状态</th>
              <th className="w-32 px-3 py-2.5 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            {entries.length === 0 && (
              <tr>
                <td
                  colSpan={9}
                  className="px-3 py-8 text-center text-xs text-[var(--yak-components-muted-text)]"
                >
                  在上方表清单勾选物理表后，这里会生成每张表的映射草稿
                </td>
              </tr>
            )}
            {entries.map((entry) => (
              <tr
                key={entry.key}
                className="border-b border-[var(--yak-datamap-divider-soft)] align-middle last:border-b-0"
              >
                <td className="px-3 py-2">
                  <Checkbox
                    aria-label={`包含 ${entry.table.name}`}
                    checked={entry.include}
                    disabled={entry.status === "created" || entry.loadingColumns}
                    onCheckedChange={(checked) => patch(entry.key, { include: checked === true })}
                  />
                </td>
                <td className="px-3 py-2">
                  <div className="min-w-0">
                    <div className="truncate font-mono text-[12.5px] font-semibold text-[var(--yak-components-panel-text)]">
                      {entry.table.name}
                    </div>
                    {entry.table.remarks?.trim() && (
                      <div className="mt-0.5 truncate text-[11px] text-[var(--yak-components-muted-text)]">
                        {entry.table.remarks}
                      </div>
                    )}
                  </div>
                </td>
                <td className="px-3 py-2 text-right text-xs text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
                  {entry.loadingColumns ? (
                    <Spinner size="small" className="ml-auto" />
                  ) : entry.columnFailed ? (
                    <span className="text-[var(--yak-components-badge-danger-text)]">读取失败</span>
                  ) : (
                    entry.rows.length
                  )}
                </td>
                <td className="px-3 py-2">
                  <Input
                    size="small"
                    variant="outlined"
                    value={entry.suffix}
                    maxLength={41}
                    disabled={entry.loadingColumns || entry.status === "created" || running}
                    aria-label={`${entry.table.name} 表名后缀`}
                    className={
                      entry.suffix.trim() && !isValidModelSuffix(entry.suffix.trim())
                        ? "border-[var(--yak-color-border-danger-subtle)]"
                        : undefined
                    }
                    onChange={(event) => patch(entry.key, { suffix: event.target.value })}
                  />
                </td>
                <td className="px-3 py-2">
                  <Input
                    size="small"
                    variant="outlined"
                    value={entry.cnName}
                    maxLength={128}
                    disabled={entry.loadingColumns || entry.status === "created" || running}
                    aria-label={`${entry.table.name} 表中文名`}
                    onChange={(event) => patch(entry.key, { cnName: event.target.value })}
                  />
                </td>
                <td className="px-3 py-2">
                  {entry.conflict === "exists" ? (
                    <Badge tone="danger">已存在</Badge>
                  ) : entry.conflict === "available" ? (
                    <Badge tone="success">可用</Badge>
                  ) : (
                    <Badge tone="neutral">核对中</Badge>
                  )}
                </td>
                <td className="px-3 py-2 text-xs text-[var(--yak-components-muted-text)]">
                  {entry.matchedStandards > 0 ? `已绑定 ${entry.matchedStandards}` : "-"}
                </td>
                <td className="px-3 py-2">
                  {entry.status === "created" ? (
                    <Badge tone="success">已创建</Badge>
                  ) : entry.status === "failed" ? (
                    <Badge tone="danger">失败</Badge>
                  ) : (
                    <Badge tone="neutral">待创建</Badge>
                  )}
                </td>
                <td className="px-3 py-2">
                  <div className="flex items-center gap-1">
                    <Button
                      size="small"
                      variant="ghost"
                      disabled={
                        entry.loadingColumns || entry.columnFailed || entry.status === "created"
                      }
                      onClick={() => setEditing(entry.key)}
                    >
                      编辑字段
                    </Button>
                    {entry.status === "failed" && (
                      <span
                        className="cursor-help text-[11px] text-[var(--yak-components-badge-danger-text)]"
                        title={entry.failure ?? undefined}
                      >
                        原因
                      </span>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <span className="text-xs text-[var(--yak-components-muted-text)]">
          {readyToCreate.length > 0
            ? `将创建 ${readyToCreate.length} 个${MODEL_TYPE_LABEL[type]}（仅保存模型，不发布物理表）`
            : "勾选物理表并核对映射后开始创建"}
        </span>
        {running && (
          <span className="text-xs text-[var(--yak-color-primary)]">
            创建中 {progress.done}/{progress.total}
          </span>
        )}
        <Button
          size="small"
          variant="primary"
          className="ml-auto"
          loading={running}
          disabled={readyToCreate.length === 0 || running}
          onClick={() => void createAll()}
        >
          批量创建
        </Button>
      </div>

      {editingEntry && (
        <Drawer open onOpenChange={(open) => !open && setEditing(null)}>
          <DrawerContent className="w-[900px] max-w-[95vw]">
            <DrawerTitle className="text-[15px] font-semibold">
              编辑字段 · {editingEntry.table.name}
              <span className="ml-2 text-xs font-normal text-[var(--yak-components-muted-text)]">
                {editingEntry.rows.length} 个字段
                {editingEntry.matchedStandards > 0
                  ? ` · 已自动绑定 ${editingEntry.matchedStandards} 项标准`
                  : ""}
              </span>
            </DrawerTitle>
            <div className="mt-4">
              <FieldEditor
                rows={editingEntry.rows}
                onChange={(rows) => patch(editingEntry.key, { rows })}
                issues={validateFieldRows(editingEntry.rows)}
                hideImportActions
              />
            </div>
            <div className="mt-4 flex justify-end">
              <Button size="small" variant="primary" onClick={() => setEditing(null)}>
                完成
              </Button>
            </div>
          </DrawerContent>
        </Drawer>
      )}
    </div>
  );
}
