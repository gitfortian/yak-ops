import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import {
  classifyPublicationFailure,
  getPublicationMetadata,
  previewPublication,
  publishModel,
  PUBLICATION_STATUS_HINT,
  PUBLICATION_STATUS_LABEL,
  type MetadataSnapshot,
  type PublicationPreview,
  type PublicationRecord,
  type PublicationStatus,
} from "@/service/data-modeling/dimension-modeling/publication";
import type { LogicalModel } from "@/service/data-modeling/dimension-modeling/types";
import { getLayerDataSources, getLayers } from "@/service/data-modeling/warehouse-planning/layers";
import { listDataSourceDatabases, listDataSourceSchemas } from "@/service/datasource";
import { requiresSchema } from "../shared/jdbc-engines";
import PublicationHistory from "./publication-history";
import { describeAddress, toTargetPayload, validatePublicationTarget } from "./publication-target";

const ENV_LABEL: Record<string, string> = { PROD: "生产", TEST: "测试", DEVELOP: "开发" };

const STATUS_TONE: Record<PublicationStatus, "info" | "success" | "danger" | "warning"> = {
  PENDING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};

interface Props {
  model: LogicalModel;
  onClose: () => void;
  /** Called after a terminal record is stored, so the caller can refresh the model it renders. */
  onPublished?: () => void;
}

/**
 * Publishes one saved logical model to one explicit physical table.
 *
 * <p>The address is chosen here, never inherited from the layer's default: the datasource comes from
 * the layer, but the database and schema are picked from live Catalog results. The generated script
 * must be rendered before the publish button unlocks, and publishing itself needs a separate
 * confirmation, because a table created here outlives the model.
 */
export default function PublishDialog({ model, onClose, onPublished }: Props) {
  const [layerName, setLayerName] = useState(model.layerName ?? "");
  const [datasourceId, setDatasourceId] = useState("");
  const [datasourceName, setDatasourceName] = useState("");
  const [dbType, setDbType] = useState<string | null>(null);
  const [connection, setConnection] = useState<string | null>(null);
  const [sourceLoading, setSourceLoading] = useState(true);
  const [sourceFailure, setSourceFailure] = useState("");

  const [databases, setDatabases] = useState<string[]>([]);
  const [database, setDatabase] = useState("");
  const [schemas, setSchemas] = useState<string[]>([]);
  const [schema, setSchema] = useState("");
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [catalogFailure, setCatalogFailure] = useState("");
  const [catalogAttempt, setCatalogAttempt] = useState(0);

  const [preview, setPreview] = useState<PublicationPreview | null>(null);
  const [previewKey, setPreviewKey] = useState("");
  const [previewing, setPreviewing] = useState(false);
  const [previewFailure, setPreviewFailure] = useState("");

  const [confirmed, setConfirmed] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publishFailure, setPublishFailure] = useState("");
  const [result, setResult] = useState<PublicationRecord | null>(null);
  const [snapshot, setSnapshot] = useState<MetadataSnapshot | null>(null);
  const [historyKey, setHistoryKey] = useState(0);

  // The layer decides where the model may land; the datasource is read from it rather than picked.
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setSourceLoading(true);
    setSourceFailure("");
    void Promise.all([getLayers(controller.signal), getLayerDataSources(controller.signal)])
      .then(([layers, sources]) => {
        if (!active) return;
        const layer = layers.find((item) => item.id === model.layerId);
        const source = sources.find((item) => item.id === layer?.dataSourceId);
        if (!layer?.dataSourceId || !source) {
          setSourceFailure("该模型所属分层未绑定物理数据源，无法发布");
          return;
        }
        setLayerName(`${layer.cnName}（${layer.enCode}）`);
        setDatasourceId(source.id);
        setDatasourceName(source.name);
        setDbType(source.dbType);
        setConnection(source.connStatus ?? null);
      })
      .catch(() => {
        if (active) setSourceFailure("分层与数据源加载失败，请稍后重试");
      })
      .finally(() => {
        if (active) setSourceLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [model.layerId]);

  useEffect(() => {
    if (!datasourceId) return;
    let active = true;
    setCatalogLoading(true);
    setCatalogFailure("");
    void listDataSourceDatabases(datasourceId)
      .then((values) => {
        if (active) setDatabases(values);
      })
      .catch(() => {
        if (!active) return;
        setDatabases([]);
        setCatalogFailure("数据库列表读取失败，请检查数据源连接");
      })
      .finally(() => {
        if (active) setCatalogLoading(false);
      });
    return () => {
      active = false;
    };
  }, [datasourceId, catalogAttempt]);

  useEffect(() => {
    if (!datasourceId || !requiresSchema(dbType)) {
      setSchemas([]);
      setSchema("");
      return;
    }
    let active = true;
    setSchemas([]);
    void listDataSourceSchemas(datasourceId, database || undefined)
      .then((values) => {
        if (active) setSchemas(values);
      })
      .catch(() => {
        if (!active) return;
        setSchemas([]);
        setCatalogFailure("Schema 列表读取失败，请检查数据源连接");
      });
    return () => {
      active = false;
    };
  }, [datasourceId, database, dbType]);

  const target = { dbType, database, schema: requiresSchema(dbType) ? schema : null };
  const targetKey = JSON.stringify(toTargetPayload(target));
  const problem = validatePublicationTarget(target);
  const addressText = describeAddress(database, schema, dbType);

  // Any change of address invalidates the script, the confirmation and the previous result: none of
  // them describe the table that would be touched now.
  useEffect(() => {
    setPreview(null);
    setPreviewKey("");
    setPreviewFailure("");
    setConfirmed(false);
    setResult(null);
    setSnapshot(null);
    setPublishFailure("");
  }, [targetKey]);

  const generate = async () => {
    if (problem) {
      setPreviewFailure(problem.message);
      return;
    }
    setPreviewing(true);
    setPreviewFailure("");
    try {
      const generated = await previewPublication(model.id, toTargetPayload(target));
      setPreview(generated);
      setPreviewKey(targetKey);
    } catch (error) {
      setPreview(null);
      setPreviewKey("");
      setPreviewFailure(classifyPublicationFailure(error).message ?? "DDL 生成失败，请稍后重试");
    } finally {
      setPreviewing(false);
    }
  };

  const publish = async () => {
    if (!preview || previewKey !== targetKey || !confirmed || publishing) return;
    setPublishing(true);
    setPublishFailure("");
    try {
      const record = await publishModel(model.id, toTargetPayload(target));
      setResult(record);
      setHistoryKey((value) => value + 1);
      onPublished?.();
      if (record.status === "SUCCEEDED") {
        toast.success("发布成功");
        try {
          setSnapshot(await getPublicationMetadata(record.id));
        } catch {
          // The table exists; only the snapshot failed. The history row offers a recollection.
          setSnapshot(null);
        }
      } else if (record.status === "UNKNOWN") {
        toast.error("结果未知，请在发布历史中对账");
      } else {
        toast.error(record.error ?? "发布失败");
      }
    } catch (error) {
      setPublishFailure(classifyPublicationFailure(error).message ?? "发布失败，请稍后重试");
    } finally {
      setPublishing(false);
    }
  };

  const canPublish = Boolean(preview) && previewKey === targetKey && confirmed && !problem;
  const disconnected = connection === "DISCONNECTED";

  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !publishing) onClose();
      }}
    >
      <DialogContent className="w-[860px] max-w-[95vw] rounded-xl p-5 shadow-none">
        <DialogTitle className="text-sm font-medium">发布到物理表</DialogTitle>
        <DialogDescription className="mt-2 text-xs leading-5 text-[#667475]">
          发布会使用已保存的模型定义生成建表脚本。目标表不存在时创建；已存在时只做结构校验，
          不会修改它，也不会删除任何数据。
        </DialogDescription>

        {sourceLoading ? (
          <div className="flex h-24 items-center justify-center">
            <Spinner />
          </div>
        ) : sourceFailure ? (
          <div className="mt-4">
            <Alert>{sourceFailure}</Alert>
          </div>
        ) : (
          <>
            <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
              <div className="grid gap-1 text-xs">
                <span className="font-medium">分层</span>
                <span className="text-[#475467]">{layerName || "-"}</span>
              </div>
              <div className="grid gap-1 text-xs">
                <span className="font-medium">数据源</span>
                <span className="text-[#475467]">
                  {datasourceName || datasourceId}（{ENV_LABEL[connection ?? ""] ?? "环境未知"}）
                </span>
              </div>
              <div className="grid gap-1 text-xs">
                <span className="font-medium">物理表</span>
                <span className="text-[#475467]">{model.name}</span>
              </div>
            </div>

            <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
              <label className="grid gap-1 text-xs">
                <span className="font-medium">目标数据库</span>
                <Select
                  value={database || null}
                  size="small"
                  disabled={catalogLoading || databases.length === 0}
                  items={databases.map((value) => ({ value, label: value }))}
                  onValueChange={(value) => setDatabase(value ?? "")}
                >
                  <SelectTrigger variant="outlined" aria-label="选择目标数据库">
                    <SelectValue placeholder={catalogLoading ? "数据库加载中" : "请选择数据库"} />
                  </SelectTrigger>
                  <SelectContent>
                    {databases.map((value) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{value}</SelectItemText>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </label>
              {requiresSchema(dbType) ? (
                <label className="grid gap-1 text-xs">
                  <span className="font-medium">目标 Schema</span>
                  <Select
                    value={schema || null}
                    size="small"
                    disabled={!database || schemas.length === 0}
                    items={schemas.map((value) => ({ value, label: value }))}
                    onValueChange={(value) => setSchema(value ?? "")}
                  >
                    <SelectTrigger variant="outlined" aria-label="选择目标 Schema">
                      <SelectValue placeholder={database ? "请选择 Schema" : "请先选择数据库"} />
                    </SelectTrigger>
                    <SelectContent>
                      {schemas.map((value) => (
                        <SelectItem key={value} value={value}>
                          <SelectItemText>{value}</SelectItemText>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </label>
              ) : (
                <div className="grid gap-1 text-xs">
                  <span className="font-medium">目标 Schema</span>
                  <span className="text-[#667475]">该引擎以数据库作为 Schema，无需单独选择。</span>
                </div>
              )}
            </div>

            {catalogFailure && (
              <div className="mt-3">
                <Alert>
                  {catalogFailure}
                  <Button size="small" onClick={() => setCatalogAttempt((value) => value + 1)}>
                    重试
                  </Button>
                </Alert>
              </div>
            )}
            {disconnected && (
              <div className="mt-3">
                <Alert>
                  该数据源连接状态为「未连接」，发布可能失败；请先在数据源页面检查连接。
                </Alert>
              </div>
            )}

            <div className="mt-4 flex items-center gap-2">
              <Button
                size="small"
                loading={previewing}
                disabled={Boolean(problem) || sourceLoading}
                onClick={() => void generate()}
              >
                生成建表脚本
              </Button>
              <span className="text-xs text-[#667475]">
                {problem
                  ? problem.message
                  : previewKey === targetKey && preview
                    ? `脚本已生成，目标 ${addressText}.${preview.table}`
                    : "先选择目标地址，再生成脚本核对"}
              </span>
            </div>
            {previewFailure && (
              <div className="mt-2">
                <Alert>{previewFailure}</Alert>
              </div>
            )}
            {preview && (
              <pre className="mt-3 max-h-56 overflow-auto rounded-lg border border-[#e4e7ec] bg-[#f9fafb] p-3 text-xs leading-5 whitespace-pre-wrap text-[#344054]">
                {preview.ddl}
              </pre>
            )}
            {preview && (
              <div className="mt-3 flex items-start gap-2 rounded-lg border border-[#e4e7ec] px-3 py-2 text-xs text-[#475467]">
                <Checkbox
                  aria-label="确认发布目标"
                  checked={confirmed}
                  onCheckedChange={(checked) => setConfirmed(checked)}
                />
                <span>
                  我确认在「{addressText}」创建或校验物理表「
                  {preview.table}」；若该表已存在，只做结构校验，不会被修改。
                </span>
              </div>
            )}
            {publishFailure && (
              <div className="mt-3">
                <Alert>{publishFailure}</Alert>
              </div>
            )}
            {result && (
              <div className="mt-3 rounded-lg border border-[#e4e7ec] px-3 py-2 text-xs text-[#475467]">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">本次发布结果</span>
                  {result.status ? (
                    <Badge tone={STATUS_TONE[result.status]}>
                      {PUBLICATION_STATUS_LABEL[result.status]}
                    </Badge>
                  ) : (
                    <Badge tone="neutral">未知状态</Badge>
                  )}
                  <span>
                    {[result.database, result.schema].filter(Boolean).join(".")}.{result.table}
                  </span>
                </div>
                <div className="mt-1">
                  {result.error ?? PUBLICATION_STATUS_HINT[result.status ?? "PENDING"]}
                </div>
                {snapshot && (
                  <div className="mt-1">
                    已采集元数据：{snapshot.tableCount} 张表 / {snapshot.fieldCount} 个字段。
                  </div>
                )}
                {result.status === "UNKNOWN" && (
                  <div className="mt-1">
                    DDL 已提交但连接中断，无法确认表是否创建。请在下方发布历史中对账，不会重跑脚本。
                  </div>
                )}
              </div>
            )}

            <div className="mt-4">
              <h3 className="text-sm font-medium">发布历史</h3>
              <p className="mt-1 text-xs text-[#667475]">
                最近 50 次发布记录。失败记录不会自动重试，「结果未知」只能对账。
              </p>
              <div className="mt-2">
                <PublicationHistory modelId={model.id} refreshKey={historyKey} />
              </div>
            </div>

            <div className="mt-4 flex justify-end gap-2">
              <Button size="small" disabled={publishing} onClick={onClose}>
                关闭
              </Button>
              <Button
                size="small"
                variant="primary"
                loading={publishing}
                disabled={!canPublish}
                onClick={() => void publish()}
              >
                发布
              </Button>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
