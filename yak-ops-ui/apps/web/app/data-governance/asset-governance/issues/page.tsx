import {
  Alert,
  Badge,
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  type TableColumns,
  toast,
} from "@yak-ops/yak-ui";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import OwnerCell from "@/app/data-map/shared/cells";
import { relativeTime } from "@/app/data-map/shared/labels";
import {
  governanceFailureMessage,
  isBatchTooLarge,
  isInvalidIssueState,
} from "@/service/data-governance/asset-governance/request";
import { pageIssues, submitBatch } from "@/service/data-governance/asset-governance/issues";
import { getRules } from "@/service/data-governance/asset-governance/overview";
import type { GovernanceIssue } from "@/service/data-governance/asset-governance/types";

import {
  governanceEvidenceSummary,
  governanceRuleLabel,
  governanceTargetLabel,
  GOVERNANCE_DIMENSION_LABEL,
  GOVERNANCE_STATUS_LABEL,
  GOVERNANCE_STATUS_TONE,
  GOVERNANCE_SEVERITY_LABEL,
  GOVERNANCE_SEVERITY_TONE,
} from "../labels";
import { AssignDialog, IgnoreDialog, ResolveDialog } from "./command-dialogs";

const PAGE_SIZE = 20;
const MAX_BATCH = 200;

type BatchCommand = "assign" | "resolve" | "reopen" | "ignore" | "unignore";

const readFilter = (value: string | null) => (value ? value : "");

export default function GovernanceIssuesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [draft, setDraft] = useState(searchParams.get("keyword") ?? "");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [command, setCommand] = useState<BatchCommand | null>(null);
  const [failure, setFailure] = useState("");

  const status = readFilter(searchParams.get("status"));
  const severity = readFilter(searchParams.get("severity"));
  const dimension = readFilter(searchParams.get("dimension"));
  const ruleCode = readFilter(searchParams.get("ruleCode"));
  const keyword = readFilter(searchParams.get("keyword"));
  const pageNo = Math.max(1, Number(searchParams.get("pageNo")) || 1);

  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(patch).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    if (!("pageNo" in patch)) next.delete("pageNo");
    setSearchParams(next, { replace: true });
  };

  const query = useMemo(
    () => ({
      status: status || undefined,
      severity: severity || undefined,
      dimension: dimension || undefined,
      ruleCode: ruleCode || undefined,
      keyword: keyword || undefined,
      pageNo,
      pageSize: PAGE_SIZE,
    }),
    [status, severity, dimension, ruleCode, keyword, pageNo],
  );

  const page = useQuery({
    queryKey: ["governance", "issues", query],
    queryFn: ({ signal }) => pageIssues(query, signal),
    placeholderData: keepPreviousData,
  });

  const rules = useQuery({
    queryKey: ["governance", "rules"],
    queryFn: ({ signal }) => getRules(signal),
  });

  const records = page.data?.bizData ?? [];
  const total = page.data?.pagination?.total ?? 0;
  const stale = page.isFetching && page.isPlaceholderData;

  const toggle = (issueId: string, checked: boolean) =>
    setSelectedIds((current) =>
      checked
        ? current.length >= MAX_BATCH
          ? current
          : [...current, issueId]
        : current.filter((id) => id !== issueId),
    );

  const batch = useMutation({
    mutationFn: (input: {
      command: BatchCommand;
      body: { ownerId?: string; ignoreUntil?: string; note?: string };
    }) => submitBatch(input.command, { issueIds: selectedIds, ...input.body }),
    onSuccess: (affected) => {
      setCommand(null);
      setSelectedIds([]);
      setFailure("");
      void queryClient.invalidateQueries({ queryKey: ["governance"] });
      toast.success(`已更新 ${affected} 个问题`);
    },
    onError: (error) => {
      setFailure(
        isBatchTooLarge(error)
          ? "一次最多操作 200 个问题。"
          : isInvalidIssueState(error)
            ? "部分问题的当前状态不支持该操作，请刷新后重试。"
            : governanceFailureMessage(error, "批量操作失败，请稍后重试"),
      );
    },
  });

  const columns: TableColumns<GovernanceIssue> = [
    {
      key: "select",
      title: "",
      width: 40,
      render: (_value, record) => (
        <input
          type="checkbox"
          aria-label={`选择 ${governanceTargetLabel(record.targetName)}`}
          checked={selectedIds.includes(record.id)}
          disabled={!selectedIds.includes(record.id) && selectedIds.length >= MAX_BATCH}
          onChange={(event) => toggle(record.id, event.target.checked)}
          onClick={(event) => event.stopPropagation()}
        />
      ),
    },
    {
      key: "rule",
      title: "规则",
      minWidth: 190,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">
            {governanceRuleLabel(record.ruleCode)}
          </div>
          <div className="mt-0.5 flex items-center gap-1.5">
            <Badge tone={GOVERNANCE_SEVERITY_TONE[record.severity]}>
              {GOVERNANCE_SEVERITY_LABEL[record.severity]}
            </Badge>
            <span className="text-[11px] text-[var(--yak-components-muted-text)]">
              {GOVERNANCE_DIMENSION_LABEL[record.dimension]}
            </span>
          </div>
        </div>
      ),
    },
    {
      key: "target",
      title: "目标",
      minWidth: 200,
      render: (_value, record) => <TargetCell issue={record} />,
    },
    {
      key: "evidence",
      title: "证据",
      minWidth: 220,
      render: (_value, record) => (
        <span className="line-clamp-2 text-xs leading-5 text-[var(--yak-components-muted-text)]">
          {governanceEvidenceSummary(record)}
        </span>
      ),
    },
    {
      key: "owner",
      title: "负责人",
      width: 120,
      render: (_value, record) => <OwnerCell ownerId={record.ownerId} />,
    },
    {
      key: "status",
      title: "状态",
      width: 92,
      render: (_value, record) => (
        <Badge tone={GOVERNANCE_STATUS_TONE[record.status]}>
          {GOVERNANCE_STATUS_LABEL[record.status]}
        </Badge>
      ),
    },
    {
      key: "lastDetected",
      title: "最近发现",
      width: 100,
      render: (_value, record) => (
        <span
          className="text-xs text-[var(--yak-components-muted-text)] [font-variant-numeric:tabular-nums]"
          title={record.lastDetectedAt}
        >
          {relativeTime(record.lastDetectedAt)}
        </span>
      ),
    },
  ];

  const commandProps = {
    pending: batch.isPending,
    onClose: () => setCommand(null),
    onSubmit: (body: { ownerId?: string; ignoreUntil?: string; note?: string }) =>
      batch.mutate({ command: command as BatchCommand, body }),
  };

  return (
    <div className="h-full min-h-0 overflow-auto bg-[var(--yak-color-background)] px-6 py-5 max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-bold tracking-tight">治理问题</h2>
        <span className="text-xs text-[var(--yak-components-muted-text)]">
          共 {total} 个 · 已选 {selectedIds.length}/{MAX_BATCH}
        </span>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2 rounded-[var(--yak-radius-lg)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-3.5 shadow-[var(--yak-datamap-card-shadow)]">
        <div className="relative w-[220px]">
          <Search
            size={13}
            aria-hidden="true"
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
          />
          <Input
            size="small"
            variant="outlined"
            value={draft}
            className="pl-8"
            placeholder="搜索目标名称"
            aria-label="搜索问题"
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") update({ keyword: draft.trim() });
            }}
          />
        </div>
        <GovernanceFilterSelect
          label="状态"
          value={status}
          onChange={(value) => update({ status: value })}
          options={Object.entries(GOVERNANCE_STATUS_LABEL).map(([value, label]) => ({
            value,
            label,
          }))}
        />
        <GovernanceFilterSelect
          label="严重度"
          value={severity}
          onChange={(value) => update({ severity: value })}
          options={Object.entries(GOVERNANCE_SEVERITY_LABEL).map(([value, label]) => ({
            value,
            label,
          }))}
        />
        <GovernanceFilterSelect
          label="维度"
          value={dimension}
          onChange={(value) => update({ dimension: value })}
          options={Object.entries(GOVERNANCE_DIMENSION_LABEL).map(([value, label]) => ({
            value,
            label,
          }))}
        />
        <GovernanceFilterSelect
          label="规则"
          value={ruleCode}
          onChange={(value) => update({ ruleCode: value })}
          options={(rules.data ?? []).map((rule) => ({
            value: rule.code,
            label: governanceRuleLabel(rule.code),
          }))}
        />
      </div>

      {selectedIds.length > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-2 rounded-[var(--yak-radius-md)] border border-[var(--yak-color-primary-ring)] bg-[var(--yak-color-primary-subtle)] px-3 py-2 text-xs text-[var(--yak-color-primary)]">
          已选 {selectedIds.length} 项
          <Button size="small" onClick={() => setCommand("assign")}>
            指派
          </Button>
          <Button size="small" onClick={() => setCommand("resolve")}>
            解决
          </Button>
          <Button size="small" onClick={() => setCommand("ignore")}>
            忽略
          </Button>
          <Button
            size="small"
            variant="ghost"
            onClick={() => batch.mutate({ command: "unignore", body: {} })}
          >
            取消忽略
          </Button>
          <Button
            size="small"
            variant="ghost"
            onClick={() => batch.mutate({ command: "reopen", body: {} })}
          >
            重新打开
          </Button>
          <Button
            size="small"
            variant="ghost"
            className="ml-auto"
            onClick={() => setSelectedIds([])}
          >
            取消选择
          </Button>
        </div>
      )}

      {failure && <Alert className="mt-3">{failure}</Alert>}
      {page.isError && (
        <Alert className="mt-3">
          {governanceFailureMessage(page.error, "问题列表加载失败，请稍后重试。")}
        </Alert>
      )}

      <div
        className={`mt-3 transition-opacity duration-150 ${stale ? "opacity-60" : "opacity-100"}`}
        aria-busy={stale || undefined}
      >
        <Table
          columns={columns}
          dataSource={records}
          rowKey="id"
          size="medium"
          loading={page.isPending}
          emptyText="没有匹配的治理问题"
          rowHoverable
          onRow={(record) => ({
            className: "cursor-pointer",
            onClick: () => navigate(`/data-governance/governance/issues/${record.id}`),
          })}
          pagination={{
            current: pageNo,
            pageSize: PAGE_SIZE,
            total,
            onChange: (nextPage) => update({ pageNo: String(nextPage) }),
          }}
          scroll={{ x: 1000 }}
        />
      </div>

      {command === "assign" && <AssignDialog {...commandProps} />}
      {command === "resolve" && <ResolveDialog {...commandProps} />}
      {command === "ignore" && <IgnoreDialog {...commandProps} />}
    </div>
  );
}

function TargetCell({ issue }: { issue: GovernanceIssue }) {
  const name = governanceTargetLabel(issue.targetName);
  if (issue.targetType === "DATA_MAP_TABLE") {
    return (
      <div className="min-w-0">
        <a
          href={`/data-governance/data-map/tables/${issue.targetId}`}
          onClick={(event) => event.stopPropagation()}
          className="block truncate font-mono text-[12.5px] font-semibold [color:var(--yak-color-primary)] hover:underline"
          title={name}
        >
          {name}
        </a>
      </div>
    );
  }
  return (
    <span
      className="block truncate font-mono text-[12.5px] text-[var(--yak-components-panel-text)]"
      title={name}
    >
      {name}
    </span>
  );
}

function GovernanceFilterSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}) {
  const all = [{ value: "", label: `全部${label}` }, ...options];
  return (
    <Select size="small" items={all} value={value} onValueChange={(next) => onChange(next ?? "")}>
      <SelectTrigger variant="outlined" className="w-[130px]">
        <span className="mr-2 shrink-0 text-[var(--yak-components-muted-text)]">{label}</span>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {all.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
