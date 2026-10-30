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
  PUBLICATION_OPERATION_LABEL,
  PUBLICATION_STATUS_HINT,
  PUBLICATION_STATUS_LABEL,
  type MetadataSnapshot,
  type PublicationPreview,
  type PublicationRecord,
  type PublicationStatus,
  type SchemaChange,
} from "@/service/data-modeling/dimension-modeling/publication";
import type { LogicalModel } from "@/service/data-modeling/dimension-modeling/types";
import {
  getLayerDataSources,
  getLayerTargets,
  getLayers,
  type LayerDataSource,
} from "@/service/data-modeling/warehouse-planning/layers";
import {
  PUBLISH_ENVIRONMENTS,
  PUBLISH_ENVIRONMENT_LABEL,
  type PublishEnvironment,
} from "./publication-target";
import PublicationHistory from "./publication-history";

const ENV_LABEL: Record<string, string> = { PROD: "生产", TEST: "测试", DEVELOP: "开发" };

const STATUS_TONE: Record<PublicationStatus, "info" | "success" | "danger" | "warning"> = {
  PENDING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};

const CHANGE_TONE: Record<SchemaChange["kind"], { color: string; label: string }> = {
  ADD: { color: "text-[#067647]", label: "🟢 新增列" },
  CHANGE: { color: "text-[#b54708]", label: "🟡 类型变更" },
  DROP: { color: "text-[#b42318]", label: "🔴 删除列" },
};

const OPERATION_TONE: Record<string, "info" | "warning" | "neutral"> = {
  CREATE: "info",
  ALTER: "warning",
  NONE: "neutral",
};

interface Props {
  model: LogicalModel;
  onClose: () => void;
  /** Called after a terminal record is stored, so the caller can refresh the model it renders. */
  onPublished?: () => void;
}

/**
 * Publishes one saved logical model through the environment its layer binds.
 *
 * <p>The user picks 开发 or 生产 and the dialog resolves the datasource and the target database from
 * the layer's per-environment binding — there is no database picker. 「生成变更对比」 fetches the live
 * table structure and renders the diff (add / change / drop) plus the exact DDL; dropping columns
 * needs a separate「我已知晓数据将丢失」acknowledgement before the publish button unlocks.
 */
export default function PublishDialog({ model, onClose, onPublished }: Props) {
  const [loading, setLoading] = useState(true);
  const [loadFailure, setLoadFailure] = useState("");
  const [layerName, setLayerName] = useState("");
  const [sources, setSources] = useState<Record<string, LayerDataSource>>({});
  const [targets, setTargets] = useState<
    Partial<
      Record<PublishEnvironment, { database: string; schema: string | null; dataSourceId: string }>
    >
  >({});

  const [environment, setEnvironment] = useState<PublishEnvironment | "">("");
  const [preview, setPreview] = useState<PublicationPreview | null>(null);
  const [previewEnv, setPreviewEnv] = useState<PublishEnvironment | "">("");
  const [previewing, setPreviewing] = useState(false);
  const [previewFailure, setPreviewFailure] = useState("");

  const [confirmed, setConfirmed] = useState(false);
  const [ackLoss, setAckLoss] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publishFailure, setPublishFailure] = useState("");
  const [result, setResult] = useState<PublicationRecord | null>(null);
  const [snapshot, setSnapshot] = useState<MetadataSnapshot | null>(null);
  const [historyKey, setHistoryKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setLoadFailure("");
    void Promise.all([
      getLayers(controller.signal),
      getLayerDataSources(controller.signal),
      getLayerTargets(model.layerId, controller.signal),
    ])
      .then(([layers, layerSources, layerTargets]) => {
        if (!active) return;
        const layer = layers.find((item) => item.id === model.layerId);
        if (!layer) {
          setLoadFailure("该模型所属分层不存在，无法发布");
          return;
        }
        setLayerName(`${layer.cnName}（${layer.enCode}）`);
        const byId = Object.fromEntries(layerSources.map((source) => [source.id, source]));
        setSources(byId);
        const bound: typeof targets = {};
        for (const target of layerTargets) {
          if (target.environment === "DEVELOP" || target.environment === "PROD") {
            bound[target.environment] = {
              database: target.database,
              schema: target.schema,
              dataSourceId: target.dataSourceId,
            };
          }
        }
        setTargets(bound);
        // The environments are ordered, so the first bound one becomes the default choice.
        setEnvironment(PUBLISH_ENVIRONMENTS.find((item) => bound[item]) ?? "");
      })
      .catch(() => {
        if (active) setLoadFailure("分层与发布目标加载失败，请稍后重试");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [model.layerId]);

  // Any change of environment invalidates the script, the diff, the acknowledgements and the
  // previous result: none of them describe the target that would be touched now.
  useEffect(() => {
    setPreview(null);
    setPreviewEnv("");
    setPreviewFailure("");
    setConfirmed(false);
    setAckLoss(false);
    setResult(null);
    setSnapshot(null);
    setPublishFailure("");
  }, [environment]);

  const bound = environment ? targets[environment] : undefined;
  const source = bound ? sources[bound.dataSourceId] : undefined;
  const addressText = bound ? [bound.database, bound.schema].filter(Boolean).join(".") : "";

  const generate = async () => {
    if (!environment || !bound) return;
    setPreviewing(true);
    setPreviewFailure("");
    try {
      const generated = await previewPublication(model.id, { environment });
      setPreview(generated);
      setPreviewEnv(environment);
    } catch (error) {
      setPreview(null);
      setPreviewEnv("");
      setPreviewFailure(
        classifyPublicationFailure(error).message ?? "变更对比生成失败，请稍后重试",
      );
    } finally {
      setPreviewing(false);
    }
  };

  const publish = async () => {
    if (!environment || !preview || previewEnv !== environment || !confirmed || publishing) return;
    if (preview.requiresDataLossAck && !ackLoss) return;
    setPublishing(true);
    setPublishFailure("");
    try {
      const record = await publishModel(model.id, {
        environment,
        acknowledgeDataLoss: preview.requiresDataLossAck,
      });
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

  const previewFresh = Boolean(preview) && previewEnv === environment && environment !== "";
  const canPublish =
    previewFresh &&
    confirmed &&
    (!preview?.requiresDataLossAck || ackLoss) &&
    !publishing &&
    preview?.operation !== "NONE" &&
    preview?.operation !== null;
  const disconnected = source?.connStatus === "DISCONNECTED";

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
          选择发布环境后，系统按分层绑定的目标库自动定位物理表：目标表不存在时生成建表语句；
          已存在且结构有差异时生成 ALTER 变更语句；结构一致时提示无需发布。
        </DialogDescription>

        {loading ? (
          <div className="flex h-24 items-center justify-center">
            <Spinner />
          </div>
        ) : loadFailure ? (
          <div className="mt-4">
            <Alert>{loadFailure}</Alert>
          </div>
        ) : (
          <>
            <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
              <label className="grid gap-1 text-xs">
                <span className="font-medium">发布环境</span>
                <Select
                  value={environment || null}
                  size="small"
                  items={PUBLISH_ENVIRONMENTS.map((value) => ({
                    value,
                    label: `${PUBLISH_ENVIRONMENT_LABEL[value]}${targets[value] ? "" : "（未绑定目标库）"}`,
                  }))}
                  onValueChange={(value) =>
                    setEnvironment((value ?? "") as PublishEnvironment | "")
                  }
                >
                  <SelectTrigger variant="outlined" aria-label="选择发布环境">
                    <SelectValue placeholder="请选择发布环境" />
                  </SelectTrigger>
                  <SelectContent>
                    {PUBLISH_ENVIRONMENTS.map((value) => (
                      <SelectItem key={value} value={value} disabled={!targets[value]}>
                        <SelectItemText>
                          {PUBLISH_ENVIRONMENT_LABEL[value]}
                          {targets[value] ? "" : "（未绑定目标库）"}
                        </SelectItemText>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </label>
              <div className="grid gap-1 text-xs">
                <span className="font-medium">物理表</span>
                <span className="text-[#475467]">{model.name}</span>
              </div>
            </div>

            {environment && !bound && (
              <div className="mt-3">
                <Alert>
                  分层「{layerName}」未绑定{PUBLISH_ENVIRONMENT_LABEL[environment]}
                  环境的发布目标，请先在数仓分层中配置。
                </Alert>
              </div>
            )}

            {bound && (
              <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                <div className="grid gap-1 text-xs">
                  <span className="font-medium">目标数据源</span>
                  <span className="text-[#475467]">
                    {source
                      ? `${source.name}（${ENV_LABEL[source.environment ?? ""] ?? "环境未知"} · ${source.dbType}）`
                      : bound.dataSourceId}
                  </span>
                </div>
                <div className="grid gap-1 text-xs">
                  <span className="font-medium">目标库（由分层绑定自动带出）</span>
                  <span className="text-[#475467]">{addressText || "-"}</span>
                </div>
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
                disabled={!bound}
                onClick={() => void generate()}
              >
                生成变更对比
              </Button>
              <span className="text-xs text-[#667475]">
                {previewFresh && preview
                  ? `已生成：目标 ${addressText}.${preview.table}`
                  : "先选择环境，再生成变更对比核对"}
              </span>
            </div>
            {previewFailure && (
              <div className="mt-2">
                <Alert>{previewFailure}</Alert>
              </div>
            )}

            {previewFresh && preview && (
              <>
                <div className="mt-3 flex items-center gap-2 text-xs">
                  <span className="font-medium">变更类型</span>
                  <Badge tone={OPERATION_TONE[preview.operation ?? "NONE"] ?? "neutral"}>
                    {PUBLICATION_OPERATION_LABEL[preview.operation ?? "NONE"]}
                  </Badge>
                  {preview.operation === "NONE" && (
                    <span className="text-[#475467]">物理表与模型结构一致，无需发布。</span>
                  )}
                </div>
                {preview.changes.length > 0 && (
                  <ul className="mt-2 grid gap-1 rounded-lg border border-[#e4e7ec] px-3 py-2 text-xs">
                    {preview.changes.map((change) => (
                      <li
                        key={`${change.kind}-${change.column}`}
                        className={CHANGE_TONE[change.kind].color}
                      >
                        {CHANGE_TONE[change.kind].label}：{change.detail}
                      </li>
                    ))}
                  </ul>
                )}
                {preview.ddl && (
                  <pre className="mt-3 max-h-56 overflow-auto rounded-lg border border-[#e4e7ec] bg-[#f9fafb] p-3 text-xs leading-5 whitespace-pre-wrap text-[#344054]">
                    {preview.ddl}
                  </pre>
                )}
                {preview.ddl && (
                  <div className="mt-3 flex items-start gap-2 rounded-lg border border-[#e4e7ec] px-3 py-2 text-xs text-[#475467]">
                    <Checkbox
                      aria-label="确认发布目标"
                      checked={confirmed}
                      onCheckedChange={(checked) => setConfirmed(checked)}
                    />
                    <span>
                      我确认执行以上 DDL，在「{addressText}」
                      {preview.operation === "CREATE" ? "创建" : "变更"}物理表「
                      {preview.table}」。
                    </span>
                  </div>
                )}
                {preview.requiresDataLossAck && (
                  <div className="mt-2 flex items-start gap-2 rounded-lg border border-[#f04438] bg-[#fef3f2] px-3 py-2 text-xs text-[#b42318]">
                    <Checkbox
                      aria-label="确认数据丢失风险"
                      checked={ackLoss}
                      onCheckedChange={(checked) => setAckLoss(checked)}
                    />
                    <span className="font-medium">
                      我已知晓本次发布包含删除列操作，相关列的数据将丢失且无法恢复。
                    </span>
                  </div>
                )}
              </>
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
                  {result.durationMs !== null && <span>耗时 {result.durationMs} ms</span>}
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
                    DDL 已提交但连接中断，无法确认引擎结果。请在下方发布历史中对账，不会重跑脚本。
                  </div>
                )}
              </div>
            )}

            <div className="mt-4">
              <h3 className="text-sm font-medium">发布历史</h3>
              <p className="mt-1 text-xs text-[#667475]">
                最近 50 次发布记录。失败记录不会自动重试，「结果未知」只能对账；点击「详情」可查看
                DDL、耗时与执行日志。
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
