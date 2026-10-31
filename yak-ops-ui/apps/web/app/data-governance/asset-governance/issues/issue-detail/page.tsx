import { Alert, Badge, Button, Spinner, toast } from "@yak-ops/yak-ui";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";

import OwnerCell from "@/app/data-map/shared/cells";
import { relativeTime } from "@/app/data-map/shared/labels";
import { governanceFailureMessage } from "@/service/data-governance/asset-governance/request";
import {
  getIssue,
  pageIssueEvents,
  submitBatch,
} from "@/service/data-governance/asset-governance/issues";
import type { GovernanceIssue } from "@/service/data-governance/asset-governance/types";

import {
  governanceEvidenceSummary,
  governanceRuleLabel,
  governanceTargetLabel,
  GOVERNANCE_DIMENSION_LABEL,
  GOVERNANCE_EVENT_LABEL,
  GOVERNANCE_SEVERITY_LABEL,
  GOVERNANCE_SEVERITY_TONE,
  GOVERNANCE_STATUS_LABEL,
  GOVERNANCE_STATUS_TONE,
} from "../../labels";
import { AssignDialog, IgnoreDialog, ResolveDialog } from "../command-dialogs";

type SingleCommand = "assign" | "resolve" | "ignore";

export default function GovernanceIssueDetailPage() {
  const { issueId = "" } = useParams();
  const queryClient = useQueryClient();
  const [command, setCommand] = useState<SingleCommand | null>(null);
  const detail = useQuery({
    queryKey: ["governance", "issue", issueId],
    queryFn: ({ signal }) => getIssue(issueId, signal),
    enabled: Boolean(issueId),
  });

  /** 事件流分页为累积式：加载更多把下一页并进列表，而不是替换。 */
  const events = useInfiniteQuery({
    queryKey: ["governance", "issue-events", issueId],
    queryFn: ({ signal, pageParam }) => pageIssueEvents(issueId, pageParam, 20, signal),
    initialPageParam: 1,
    getNextPageParam: (lastPage, allPages) =>
      lastPage.pagination.total > allPages.length * 20 ? allPages.length + 1 : undefined,
    enabled: Boolean(issueId),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["governance"] });
  };

  const act = useMutation({
    mutationFn: (input: {
      command: SingleCommand;
      body: { ownerId?: string; ignoreUntil?: string };
    }) => submitBatch(input.command, { issueIds: [issueId], ...input.body }),
    onSuccess: () => {
      setCommand(null);
      invalidate();
      toast.success("已更新");
    },
    onError: (error) => toast.error(governanceFailureMessage(error, "操作失败，请稍后重试")),
  });

  const record = detail.data?.issue;
  const rule = detail.data?.rule;

  const actions = (issue: GovernanceIssue) => {
    const items: { key: SingleCommand; label: string; primary?: boolean }[] = [];
    if (issue.status === "OPEN" || issue.status === "HANDLING") {
      items.push({ key: "assign", label: issue.ownerId ? "改派" : "指派" });
      items.push({ key: "resolve", label: "解决", primary: true });
      items.push({ key: "ignore", label: "忽略" });
    }
    if (issue.status === "IGNORED") {
      items.push({ key: "assign", label: "指派" });
      items.push({ key: "ignore", label: "调整忽略期限" });
    }
    if (issue.status === "RESOLVED") {
      items.push({ key: "assign", label: "指派" });
    }
    return items;
  };

  const eventRows = useMemo(
    () => (events.data?.pages ?? []).flatMap((page) => page.bizData),
    [events.data],
  );

  const commandProps = {
    pending: act.isPending,
    onClose: () => setCommand(null),
    onSubmit: (body: { ownerId?: string; ignoreUntil?: string }) =>
      act.mutate({ command: command as SingleCommand, body }),
  };

  return (
    <div className="h-full min-h-0 overflow-auto bg-[var(--yak-color-background)] px-6 py-5 max-md:px-4">
      <Link
        to="/data-governance/governance/issues"
        className="inline-flex items-center gap-1 text-xs text-[var(--yak-components-muted-text)] hover:[color:var(--yak-color-primary)]"
      >
        <ArrowLeft size={13} aria-hidden="true" />
        治理问题
      </Link>

      {detail.isPending && (
        <div className="flex items-center gap-2 py-16 text-xs text-[var(--yak-components-muted-text)]">
          <Spinner size="small" />
          正在加载问题
        </div>
      )}

      {detail.isError && (
        <Alert className="mt-4">
          {governanceFailureMessage(detail.error, "问题加载失败，请稍后重试。")}
          <Button size="small" className="ml-3" onClick={() => void detail.refetch()}>
            重试
          </Button>
        </Alert>
      )}

      {record && rule && (
        <>
          <div className="mt-3 rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-5 shadow-[var(--yak-datamap-card-shadow)]">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-base font-bold tracking-tight">
                    {governanceRuleLabel(record.ruleCode)}
                  </h2>
                  <Badge tone={GOVERNANCE_SEVERITY_TONE[record.severity]}>
                    {GOVERNANCE_SEVERITY_LABEL[record.severity]}严重度
                  </Badge>
                  <Badge tone={GOVERNANCE_STATUS_TONE[record.status]}>
                    {GOVERNANCE_STATUS_LABEL[record.status]}
                  </Badge>
                  <span className="text-xs text-[var(--yak-components-muted-text)]">
                    {GOVERNANCE_DIMENSION_LABEL[record.dimension]} · 规则 v{record.ruleVersion}
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs">
                  <span className="text-[var(--yak-components-muted-text)]">
                    目标：
                    {record.targetType === "DATA_MAP_TABLE" ? (
                      <Link
                        to={`/data-governance/data-map/tables/${record.targetId}`}
                        className="font-mono font-semibold [color:var(--yak-color-primary)] hover:underline"
                      >
                        {governanceTargetLabel(record.targetName)}
                      </Link>
                    ) : (
                      <span className="font-mono font-semibold text-[var(--yak-components-panel-text)]">
                        {governanceTargetLabel(record.targetName)}
                      </span>
                    )}
                  </span>
                  <span className="inline-flex items-center gap-1.5 text-[var(--yak-components-muted-text)]">
                    负责人：
                    <OwnerCell ownerId={record.ownerId} />
                  </span>
                  {record.ignoreUntil && (
                    <span className="text-[var(--yak-components-muted-text)]">
                      忽略至 {record.ignoreUntil.slice(0, 10)}
                    </span>
                  )}
                  {record.resolvedAt && (
                    <span className="text-[var(--yak-components-muted-text)]">
                      解决于 {relativeTime(record.resolvedAt)}（
                      {record.resolution === "AUTO" ? "自动" : "手动"}）
                    </span>
                  )}
                </div>
              </div>

              <div className="flex shrink-0 flex-wrap items-center gap-1.5">
                {actions(record).map((item) => (
                  <Button
                    key={item.key}
                    size="small"
                    variant={item.primary ? "primary" : "secondary"}
                    onClick={() => setCommand(item.key)}
                  >
                    {item.label}
                  </Button>
                ))}
              </div>
            </div>

            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,1fr)_240px]">
              <div className="rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)] px-4 py-3">
                <h3 className="text-xs font-semibold uppercase tracking-wide text-[var(--yak-components-muted-text)]">
                  证据
                </h3>
                <p className="mt-2 text-sm leading-6 text-[var(--yak-components-panel-text)]">
                  {governanceEvidenceSummary(record)}
                </p>
                <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-xs text-[var(--yak-components-muted-text)]">
                  <span>观测值 {record.observedValue ?? "-"}</span>
                  <span>阈值 {record.thresholdValue ?? "-"}</span>
                  <span>样本 {record.sampleCount ?? "-"}</span>
                  <span>窗口 {record.windowDays ?? "-"} 天</span>
                </div>
              </div>
              <div className="rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4">
                <h3 className="text-xs font-semibold uppercase tracking-wide text-[var(--yak-components-muted-text)]">
                  时间
                </h3>
                <dl className="mt-2.5 space-y-2 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <dt className="text-[var(--yak-components-muted-text)]">首次发现</dt>
                    <dd title={record.firstDetectedAt}>{relativeTime(record.firstDetectedAt)}</dd>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <dt className="text-[var(--yak-components-muted-text)]">最近发现</dt>
                    <dd title={record.lastDetectedAt}>{relativeTime(record.lastDetectedAt)}</dd>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <dt className="text-[var(--yak-components-muted-text)]">权重</dt>
                    <dd>{rule.weight}</dd>
                  </div>
                </dl>
              </div>
            </div>
          </div>

          <div className="mt-4 rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-5 shadow-[var(--yak-datamap-card-shadow)]">
            <h3 className="text-[13px] font-bold">事件流</h3>
            <div className="mt-3">
              {events.isPending ? (
                <div className="flex items-center gap-2 py-6 text-xs text-[var(--yak-components-muted-text)]">
                  <Spinner size="small" />
                  正在加载事件
                </div>
              ) : eventRows.length === 0 ? (
                <p className="py-6 text-center text-xs text-[var(--yak-components-muted-text)]">
                  暂无事件
                </p>
              ) : (
                <ol className="m-0 list-none p-0">
                  {eventRows.map((event, index) => (
                    <li key={event.id} className="relative flex gap-3 pb-5 last:pb-0">
                      {index < eventRows.length - 1 && (
                        <span
                          aria-hidden="true"
                          className="absolute top-4 left-[5px] h-full w-px bg-[var(--yak-components-panel-border)]"
                        />
                      )}
                      <span
                        aria-hidden="true"
                        className="relative z-10 mt-1.5 size-2.5 shrink-0 rounded-full bg-[var(--yak-color-primary)]"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-[13px] font-semibold text-[var(--yak-components-panel-text)]">
                            {GOVERNANCE_EVENT_LABEL[event.eventType] ?? event.eventType}
                          </span>
                          <span className="text-xs tabular-nums text-[var(--yak-components-muted-text)]">
                            {event.createTime}
                          </span>
                          {event.toStatus && (
                            <Badge tone="neutral">
                              {GOVERNANCE_STATUS_LABEL[
                                event.toStatus as keyof typeof GOVERNANCE_STATUS_LABEL
                              ] ?? event.toStatus}
                            </Badge>
                          )}
                        </div>
                        {event.note && (
                          <p className="mt-1 text-sm leading-5 text-[var(--yak-components-panel-text)]">
                            {event.note}
                          </p>
                        )}
                        {event.ignoreUntil && (
                          <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                            忽略至 {event.ignoreUntil.slice(0, 10)}
                          </p>
                        )}
                      </div>
                    </li>
                  ))}
                </ol>
              )}
              {events.hasNextPage && (
                <div className="mt-2 text-center">
                  <Button
                    size="small"
                    variant="ghost"
                    loading={events.isFetchingNextPage}
                    onClick={() => void events.fetchNextPage()}
                  >
                    加载更多
                  </Button>
                </div>
              )}
            </div>
          </div>
        </>
      )}

      {command === "assign" && <AssignDialog {...commandProps} />}
      {command === "resolve" && <ResolveDialog {...commandProps} />}
      {command === "ignore" && <IgnoreDialog {...commandProps} />}
    </div>
  );
}
