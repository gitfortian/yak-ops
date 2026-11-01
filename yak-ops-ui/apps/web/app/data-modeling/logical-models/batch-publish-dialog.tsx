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
import { useEffect, useMemo, useRef, useState } from "react";
import {
  classifyPublicationFailure,
  previewPublication,
  publishModel,
  PUBLICATION_OPERATION_LABEL,
  PUBLICATION_STATUS_LABEL,
  type PublicationPreview,
  type PublicationRecord,
  type PublicationStatus,
} from "@/service/data-modeling/dimension-modeling/publication";
import type { LogicalModel } from "@/service/data-modeling/dimension-modeling/types";
import {
  getLayerTargets,
  type LayerTarget,
} from "@/service/data-modeling/warehouse-planning/layers";
import {
  PUBLISH_ENVIRONMENTS,
  PUBLISH_ENVIRONMENT_LABEL,
  type PublishEnvironment,
} from "./publication-target";

type PreviewOutcome =
  | { kind: "pending" }
  | { kind: "failed"; message: string }
  | { kind: "ready"; preview: PublicationPreview };

const OPERATION_TONE: Record<string, "info" | "warning" | "neutral"> = {
  CREATE: "info",
  ALTER: "warning",
  NONE: "neutral",
};

const STATUS_TONE: Record<PublicationStatus, "info" | "success" | "danger" | "warning"> = {
  PENDING: "info",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};

interface Props {
  models: LogicalModel[];
  onClose: () => void;
  /** 任一模型产生终态记录后回调，调用方据此刷新工作台。 */
  onPublished: () => void;
}

/**
 * 批量发布：对一批模型并发预览、汇总变更与危险项、统一确认后逐个发布。
 *
 * <p>预览与发布都走既有的单模型契约；这里补齐的是批量视角——一次看到每张目标表是
 * CREATE / ALTER / NONE，DROP 类危险变更统一一次确认，而不是逐模型点开弹窗。
 */
export default function BatchPublishDialog({ models, onClose, onPublished }: Props) {
  const [environment, setEnvironment] = useState<PublishEnvironment | "">("");
  const [phase, setPhase] = useState<"idle" | "previewing" | "ready" | "publishing" | "done">(
    "idle",
  );
  const [outcomes, setOutcomes] = useState<Record<string, PreviewOutcome>>({});
  const [records, setRecords] = useState<Record<string, PublicationRecord>>({});
  const [confirmed, setConfirmed] = useState(false);
  const [ackLoss, setAckLoss] = useState(false);
  const [failure, setFailure] = useState("");
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const previewToken = useRef(0);
  const publishedAny = useRef(false);

  // 每个涉及的分层读一次目标绑定，环境选择只列出所有模型都能发布的环境。
  const layerIds = useMemo(() => [...new Set(models.map((model) => model.layerId))], [models]);
  const [targets, setTargets] = useState<Record<string, LayerTarget[]> | null>(null);
  const [targetsFailed, setTargetsFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setTargets(null);
    setTargetsFailed(false);
    void Promise.all(layerIds.map((id) => getLayerTargets(id, controller.signal)))
      .then((groups) => {
        if (!active) return;
        const byLayer: Record<string, LayerTarget[]> = {};
        layerIds.forEach((id, index) => {
          byLayer[id] = groups[index];
        });
        setTargets(byLayer);
      })
      .catch(() => {
        if (active) setTargetsFailed(true);
      });
    return () => {
      active = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layerIds.join("|")]);

  const targetOf = (model: LogicalModel) => {
    const group = targets?.[model.layerId];
    return group?.find((target) => target.environment === environment) ?? null;
  };

  const environments = useMemo(() => {
    const bound = new Set<PublishEnvironment>();
    models.forEach((model) => {
      (targets?.[model.layerId] ?? []).forEach((target) => {
        if (target.environment === "DEVELOP" || target.environment === "PROD") {
          bound.add(target.environment);
        }
      });
    });
    return PUBLISH_ENVIRONMENTS.filter((environment) => bound.has(environment));
  }, [models, targets]);

  // 环境变化即作废全部预览与确认。
  useEffect(() => {
    setOutcomes({});
    setPhase("idle");
    setConfirmed(false);
    setAckLoss(false);
    setRecords({});
    setFailure("");
  }, [environment]);

  const previewable = models.filter((model) => targetOf(model) != null);

  const runPreviews = async () => {
    if (!environment) return;
    const token = ++previewToken.current;
    setPhase("previewing");
    setFailure("");
    const queue = [...previewable];
    const next: Record<string, PreviewOutcome> = {};
    const worker = async () => {
      while (queue.length > 0) {
        const model = queue.shift();
        if (!model || previewToken.current !== token) return;
        try {
          const preview = await previewPublication(model.id, { environment });
          next[model.id] = { kind: "ready", preview };
        } catch (error) {
          next[model.id] = {
            kind: "failed",
            message: classifyPublicationFailure(error).message ?? "预览失败",
          };
        }
        if (previewToken.current !== token) return;
        setOutcomes((current) => ({ ...current, ...next }));
        setProgress((previous) => ({ done: previous.done + 1, total: previewable.length }));
      }
    };
    setProgress({ done: 0, total: previewable.length });
    await Promise.all([worker(), worker(), worker()]);
    if (previewToken.current !== token) return;
    setPhase("ready");
    setProgress({ done: previewable.length, total: previewable.length });
  };

  const publishable = models.filter((model) => {
    const outcome = outcomes[model.id];
    return outcome?.kind === "ready" && outcome.preview.operation !== "NONE";
  });
  const lossModels = publishable.filter((model) => {
    const outcome = outcomes[model.id];
    return outcome?.kind === "ready" && outcome.preview.requiresDataLossAck;
  });

  const runPublishes = async () => {
    setPhase("publishing");
    setFailure("");
    setProgress({ done: 0, total: publishable.length });
    let failed = 0;
    for (const model of publishable) {
      const outcome = outcomes[model.id];
      const preview = outcome?.kind === "ready" ? outcome.preview : null;
      if (!preview) continue;
      try {
        const record = await publishModel(model.id, {
          environment,
          acknowledgeDataLoss: preview.requiresDataLossAck,
        });
        setRecords((current) => ({ ...current, [model.id]: record }));
        if (record.status === "SUCCEEDED") publishedAny.current = true;
        else failed += 1;
      } catch {
        // 单模型失败不中断批次；失败数在结果里逐行呈现。
        failed += 1;
      }
      setProgress((previous) => ({ ...previous, done: previous.done + 1 }));
    }
    setPhase("done");
    onPublished();
    if (failed === 0) toast.success(`批量发布完成：${publishable.length} 个模型`);
    else toast.warning(`批量发布完成：${publishable.length - failed} 成功，${failed} 失败`);
  };

  const canConfirm = phase === "ready" && confirmed && (lossModels.length === 0 || ackLoss);
  const noneCount = models.filter((model) => {
    const outcome = outcomes[model.id];
    return outcome?.kind === "ready" && outcome.preview.operation === "NONE";
  }).length;

  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && phase !== "publishing") onClose();
      }}
    >
      <DialogContent className="w-[860px] max-w-[95vw]">
        <DialogTitle className="text-[15px] font-semibold">批量发布</DialogTitle>
        <DialogDescription className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
          对一批模型统一生成变更对比并逐个发布；发布仍走单模型契约，目标库由分层绑定决定。
        </DialogDescription>

        {targetsFailed ? (
          <Alert>分层目标加载失败，请稍后重试。</Alert>
        ) : targets === null ? (
          <div className="flex h-20 items-center justify-center">
            <Spinner />
          </div>
        ) : (
          <>
            <div className="mt-4 flex flex-wrap items-center gap-3">
              <label className="grid gap-1 text-xs">
                <span className="font-medium">发布环境</span>
                <Select
                  value={environment || null}
                  size="small"
                  items={environments.map((value) => ({
                    value,
                    label: PUBLISH_ENVIRONMENT_LABEL[value],
                  }))}
                  onValueChange={(value) =>
                    setEnvironment((value ?? "") as PublishEnvironment | "")
                  }
                >
                  <SelectTrigger variant="outlined" aria-label="选择发布环境" className="w-[200px]">
                    <SelectValue placeholder="请选择发布环境" />
                  </SelectTrigger>
                  <SelectContent>
                    {environments.map((value) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{PUBLISH_ENVIRONMENT_LABEL[value]}</SelectItemText>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </label>
              {environment && phase !== "publishing" && phase !== "done" && (
                <Button
                  size="small"
                  className="self-end"
                  loading={phase === "previewing"}
                  disabled={previewable.length === 0}
                  onClick={() => void runPreviews()}
                >
                  生成全部预览
                </Button>
              )}
              <span className="self-end text-xs text-[var(--yak-components-muted-text)]">
                {models.length} 个模型 · {previewable.length} 个可发布
              </span>
              {phase === "previewing" && (
                <span className="text-xs text-[var(--yak-color-primary)]">
                  预览中 {progress.done}/{progress.total}
                </span>
              )}
              {phase === "publishing" && (
                <span className="text-xs text-[var(--yak-color-primary)]">
                  发布中 {progress.done}/{progress.total}
                </span>
              )}
            </div>

            {previewable.length === 0 && environment && (
              <Alert>
                所选环境下没有可发布的模型：请先在数仓规划中为各分层绑定该环境的目标库。
              </Alert>
            )}

            <div className="mt-4 max-h-[380px] overflow-auto rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)]">
              <table className="w-full text-[13px]">
                <thead>
                  <tr className="border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)] text-left text-xs text-[var(--yak-components-muted-text)]">
                    <th className="px-3 py-2 font-medium">模型</th>
                    <th className="w-24 px-3 py-2 font-medium">变更类型</th>
                    <th className="w-20 px-3 py-2 text-right font-medium">变更列</th>
                    <th className="px-3 py-2 font-medium">目标表</th>
                    <th className="w-24 px-3 py-2 font-medium">结果</th>
                  </tr>
                </thead>
                <tbody>
                  {models.map((model) => {
                    const outcome = outcomes[model.id];
                    const preview = outcome?.kind === "ready" ? outcome.preview : null;
                    const record = records[model.id];
                    const target = targetOf(model);
                    const address = target
                      ? [target.database, target.schema].filter(Boolean).join(".")
                      : "-";
                    return (
                      <tr
                        key={model.id}
                        className="border-b border-[var(--yak-datamap-divider-soft)] last:border-b-0"
                      >
                        <td className="px-3 py-2">
                          <div className="truncate font-mono text-[12.5px] font-semibold text-[var(--yak-components-panel-text)]">
                            {model.name}
                          </div>
                          {outcome?.kind === "failed" && (
                            <div className="mt-0.5 text-[11px] text-[var(--yak-components-badge-danger-text)]">
                              {outcome.message}
                            </div>
                          )}
                        </td>
                        <td className="px-3 py-2">
                          {preview ? (
                            <Badge tone={OPERATION_TONE[preview.operation ?? "NONE"] ?? "neutral"}>
                              {PUBLICATION_OPERATION_LABEL[preview.operation ?? "NONE"]}
                            </Badge>
                          ) : outcome?.kind === "pending" || phase === "previewing" ? (
                            <Spinner size="small" />
                          ) : (
                            <span className="text-xs text-[var(--yak-components-muted-text)]">
                              -
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-2 text-right text-xs [font-variant-numeric:tabular-nums]">
                          {preview ? preview.changes.length : "-"}
                        </td>
                        <td className="px-3 py-2 text-xs text-[var(--yak-components-muted-text)]">
                          {preview ? `${address}.${preview.table}` : address}
                        </td>
                        <td className="px-3 py-2">
                          {record ? (
                            <Badge tone={STATUS_TONE[record.status ?? "PENDING"]}>
                              {PUBLICATION_STATUS_LABEL[record.status ?? "PENDING"]}
                            </Badge>
                          ) : preview?.requiresDataLossAck ? (
                            <Badge tone="danger">删列</Badge>
                          ) : preview?.operation === "NONE" ? (
                            <span className="text-xs text-[var(--yak-components-muted-text)]">
                              无需发布
                            </span>
                          ) : null}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {noneCount > 0 && (
              <p className="mt-2 text-xs text-[var(--yak-components-muted-text)]">
                {noneCount} 个模型与物理表结构一致，将跳过发布。
              </p>
            )}

            {phase === "ready" && publishable.length > 0 && (
              <div className="mt-3 space-y-2">
                <div className="flex items-start gap-2 rounded-lg border border-[var(--yak-components-panel-border)] px-3 py-2 text-xs text-[var(--yak-components-field-label)]">
                  <Checkbox
                    aria-label="确认执行批量发布"
                    checked={confirmed}
                    onCheckedChange={(checked) => setConfirmed(checked === true)}
                  />
                  <span>
                    我确认对上述 {publishable.length} 个模型
                    {environment === "PROD" && (
                      <span className="text-[var(--yak-components-badge-danger-text)]">
                        （生产环境，直接作用于生产库）
                      </span>
                    )}
                    逐个执行变更。
                  </span>
                </div>
                {lossModels.length > 0 && (
                  <div className="flex items-start gap-2 rounded-lg border border-[var(--yak-color-border-danger-subtle)] bg-[var(--yak-components-badge-danger-bg)] px-3 py-2 text-xs text-[var(--yak-components-badge-danger-text)]">
                    <Checkbox
                      aria-label="确认数据丢失风险"
                      checked={ackLoss}
                      onCheckedChange={(checked) => setAckLoss(checked === true)}
                    />
                    <span className="font-medium">
                      {lossModels.length} 个模型包含删除列操作，相关列的数据将丢失且无法恢复（
                      {lossModels.map((model) => model.name).join("、")}）。
                    </span>
                  </div>
                )}
              </div>
            )}

            {failure && <Alert className="mt-3">{failure}</Alert>}

            {phase === "done" && (
              <div className="mt-3 rounded-lg border border-[var(--yak-components-panel-border)] px-3 py-2 text-xs text-[var(--yak-components-field-label)]">
                批量发布结束。发布不会登记资产目录；需在「元数据采集」运行采集任务后资产才会出现在资产目录。
                <Button
                  size="small"
                  variant="ghost"
                  className="ml-2 font-normal"
                  onClick={() => onClose()}
                >
                  关闭
                </Button>
              </div>
            )}

            <div className="mt-4 flex justify-end gap-2">
              <Button size="small" disabled={phase === "publishing"} onClick={onClose}>
                {phase === "done" ? "关闭" : "取消"}
              </Button>
              {phase === "ready" && (
                <Button
                  size="small"
                  variant="primary"
                  disabled={!canConfirm}
                  onClick={() => void runPublishes()}
                >
                  逐个发布 {publishable.length} 个模型
                </Button>
              )}
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
