import { useCallback, useEffect, useState } from "react";
import {
  createRelation,
  deleteRelation,
  listRelations,
  proposeRelationKeys,
  updateRelation,
  type ModelRelation,
  type RelationKeyPair,
  type RelationProposal,
} from "@/service/data-modeling/semantic";
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { getModels } from "@/service/data-modeling/dimension-modeling";

/**
 * 关系页签：按目标维表分组的卡片列表，左边框颜色即健康状态；抽屉内建关系并消费键对提案。
 * 无图形——一跳多对一范围内卡片的信息密度高于任何图。
 */
export function ModelRelationsPanel({
  modelId,
  revision,
  onRequestFields,
}: {
  modelId: string;
  revision: number;
  /** Takes the user to the field editor, the only place a standard can actually be bound. */
  onRequestFields: () => void;
}) {
  const [relations, setRelations] = useState<ModelRelation[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<ModelRelation | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    listRelations(modelId, controller.signal)
      .then((rows) => {
        if (active) {
          setRelations(rows);
          setFailed(false);
        }
      })
      .catch(() => {
        if (active) setFailed(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [modelId, version, revision]);

  const reload = useCallback(() => setVersion((value) => value + 1), []);

  const remove = async (relation: ModelRelation) => {
    if (!window.confirm(`删除关系「${relation.name}」？该操作不可撤销。`)) return;
    try {
      await deleteRelation(relation.id, relation.revision);
      reload();
    } catch (error) {
      window.alert(error instanceof Error ? error.message : "删除失败");
    }
  };

  const ordered = [...relations].sort(
    (a, b) => healthRank(a) - healthRank(b) || a.toModelName.localeCompare(b.toModelName),
  );
  const blocked = ordered.filter((relation) => relation.status !== "READY").length;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-4 py-2">
          <p className="text-xs text-[var(--yak-components-field-label)]">关系</p>
          <p className="text-xl font-medium">{relations.length}</p>
        </div>
        <div className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-4 py-2">
          <p className="text-xs text-[var(--yak-components-field-label)]">键已绑标准</p>
          <p className="text-xl font-medium text-[var(--yak-components-badge-success-text)]">
            {relations.length - blocked} / {relations.length}
          </p>
        </div>
        <div className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-4 py-2">
          <p className="text-xs text-[var(--yak-components-field-label)]">待修复</p>
          <p className="text-xl font-medium text-[var(--yak-components-badge-warning-text)]">
            {blocked}
          </p>
        </div>
      </div>

      <div className="rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)]">
        <div className="flex items-center justify-between border-b border-[var(--yak-components-panel-border)] px-4 py-2.5">
          <h3 className="text-sm font-medium">关系</h3>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--yak-components-field-label)]">
              按健康状态排序 · 同一目标维表相邻
            </span>
            <Button size="small" variant="primary" onClick={() => setCreating(true)}>
              新建关系
            </Button>
          </div>
        </div>
        <div className="flex flex-col gap-2.5 p-3">
          {loading && (
            <p className="p-3 text-xs text-[var(--yak-components-field-label)]">加载中…</p>
          )}
          {failed && (
            <p className="p-3 text-xs text-[var(--yak-components-badge-danger-text)]">
              关系加载失败，请稍后重试。
            </p>
          )}
          {!loading && !failed && ordered.length === 0 && (
            <p className="p-3 text-xs leading-5 text-[var(--yak-components-field-label)]">
              这个模型还没有关系。关系决定它能否和其他模型一起被查询。
            </p>
          )}
          {ordered.map((relation) => (
            <RelationCard
              key={relation.id}
              relation={relation}
              onEdit={() => setEditing(relation)}
              onFixStandard={onRequestFields}
              onDelete={() => remove(relation)}
            />
          ))}
        </div>
      </div>

      {creating && (
        <RelationDrawer
          modelId={modelId}
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false);
            reload();
          }}
        />
      )}
      {editing && (
        <RelationDrawer
          modelId={modelId}
          relation={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            reload();
          }}
        />
      )}
    </div>
  );
}

function healthRank(relation: ModelRelation) {
  if (relation.status === "FIELD_REMOVED") return 0;
  if (relation.status === "KEY_UNSTANDARDIZED") return 1;
  return 2;
}

const BORDER: Record<ModelRelation["status"], string> = {
  READY: "border-l-[var(--yak-components-badge-success-text)]",
  KEY_UNSTANDARDIZED: "border-l-[var(--yak-components-badge-warning-text)]",
  FIELD_REMOVED: "border-l-[var(--yak-components-badge-danger-text)]",
};

const STATUS_LABEL: Record<
  ModelRelation["status"],
  { text: string; tone: "success" | "warning" | "danger" }
> = {
  READY: { text: "已绑标准", tone: "success" },
  KEY_UNSTANDARDIZED: { text: "键未绑标准", tone: "warning" },
  FIELD_REMOVED: { text: "字段已移除", tone: "danger" },
};

function RelationCard({
  relation,
  onEdit,
  onFixStandard,
  onDelete,
}: {
  relation: ModelRelation;
  onEdit: () => void;
  onFixStandard: () => void;
  onDelete: () => void;
}) {
  const status = STATUS_LABEL[relation.status];
  const unboundKey = relation.keys.find((key) => !key.resolved || !key.standardBound);
  const unboundName = unboundKey
    ? !unboundKey.resolved
      ? "字段已移除"
      : `${unboundKey.resolved && !unboundKey.standardBound ? unboundKey.fromFieldCode : ""}未绑标准`.trim()
    : null;
  return (
    <div
      className={`rounded-lg border border-[var(--yak-components-panel-border)] border-l-[3px] bg-white p-3 ${BORDER[relation.status]}`}
    >
      <div className="mb-1.5 flex items-start justify-between">
        <div>
          <p className="text-[13px] font-medium text-[var(--yak-components-text)]">
            {relation.toModelName}
          </p>
          <p className="mt-0.5 text-[11px] text-[var(--yak-components-field-label)]">
            {relation.toModelType} · {relation.toModelId}
          </p>
        </div>
        <span
          className={
            "rounded px-2 py-0.5 text-[11px] " +
            (relation.status === "READY"
              ? "bg-[var(--yak-components-badge-success-bg)] text-[var(--yak-components-badge-success-text)]"
              : relation.status === "KEY_UNSTANDARDIZED"
                ? "bg-[var(--yak-components-badge-warning-bg)] text-[var(--yak-components-badge-warning-text)]"
                : "bg-[var(--yak-components-badge-danger-bg)] text-[var(--yak-components-badge-danger-text)]")
          }
        >
          {relation.status === "KEY_UNSTANDARDIZED" && unboundKey
            ? `${unboundName ?? "键未绑标准"}`
            : status.text}
        </span>
      </div>
      <p className="mb-1.5 text-xs text-[var(--yak-components-field-label)]">
        <span className="font-mono">{relation.joinType} JOIN</span>
        {relation.description ? ` · ${relation.description}` : ""}
      </p>
      <div className="mb-2.5 rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-surface-muted)] px-2.5 py-1.5 font-mono text-xs">
        {relation.keys.map((key, index) => (
          <span key={key.ordinal}>
            {index > 0 && ", "}
            {key.fromFieldCode}{" "}
            <span className="text-[var(--yak-components-field-label)]">(事实)</span> ={" "}
            {key.toFieldCode}{" "}
            <span className="text-[var(--yak-components-field-label)]">(维表)</span>
            {!key.resolved && (
              <span className="text-[var(--yak-components-badge-danger-text)]">（字段已移除）</span>
            )}
          </span>
        ))}
      </div>
      <div className="flex gap-2">
        {relation.status === "KEY_UNSTANDARDIZED" && (
          <Button
            size="small"
            className="border-[var(--yak-components-badge-warning-text)] text-[var(--yak-components-badge-warning-text)]"
            onClick={onFixStandard}
          >
            去补标准
          </Button>
        )}
        <Button size="small" onClick={onEdit}>
          编辑关系
        </Button>
        <Button size="small" onClick={onDelete}>
          删除
        </Button>
      </div>
    </div>
  );
}

function RelationDrawer({
  modelId,
  relation,
  onClose,
  onSaved,
}: {
  modelId: string;
  relation?: ModelRelation;
  onClose: () => void;
  onSaved: () => void;
}) {
  const editing = Boolean(relation);
  const [targetModelId, setTargetModelId] = useState(relation?.toModelId ?? "");
  const [name, setName] = useState(relation?.name ?? "");
  const [joinType, setJoinType] = useState(relation?.joinType ?? "LEFT");
  const [description, setDescription] = useState(relation?.description ?? "");
  const [pairs, setPairs] = useState<RelationKeyPair[]>(
    relation?.keys.map((key) => ({
      fromFieldCode: key.fromFieldCode,
      toFieldCode: key.toFieldCode,
    })) ?? [],
  );
  const [proposals, setProposals] = useState<RelationProposal[] | null>(null);
  const [manualFrom, setManualFrom] = useState("");
  const [manualTo, setManualTo] = useState("");
  const [dimCandidates, setDimCandidates] = useState<{ id: string; name: string }[]>([]);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const loadProposals = useCallback(() => {
    if (!targetModelId) return;
    proposeRelationKeys(modelId, targetModelId)
      .then(setProposals)
      .catch(() => setProposals([]));
  }, [modelId, targetModelId]);

  useEffect(() => {
    if (!editing && targetModelId) loadProposals();
  }, [editing, targetModelId, loadProposals]);

  useEffect(() => {
    if (editing) return;
    const controller = new AbortController();
    getModels(
      { type: "DIM", layerId: "", keyword: "", pageNo: 1, pageSize: 100 },
      controller.signal,
    )
      .then((page) =>
        setDimCandidates(page.bizData.map((row) => ({ id: row.id, name: row.cnName }))),
      )
      .catch(() => setDimCandidates([]));
    return () => controller.abort();
  }, [editing]);

  const save = async () => {
    setFailure(null);
    if (!targetModelId) {
      setFailure("请选择目标维度表");
      return;
    }
    if (pairs.length === 0) {
      setFailure("请至少添加一组关联键");
      return;
    }
    setBusy(true);
    try {
      if (editing && relation) {
        await updateRelation(relation.id, {
          name: name || `${relation.fromModelName}_to_${relation.toModelName}`,
          joinType,
          description: description || undefined,
          keys: pairs,
          revision: relation.revision,
        });
      } else {
        await createRelation(modelId, {
          targetModelId,
          name: name || undefined,
          joinType,
          description: description || undefined,
          keys: pairs,
        });
      }
      onSaved();
    } catch (error) {
      setFailure(error instanceof Error ? error.message : "保存失败，请稍后重试");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogTitle>{editing ? "编辑关系" : "新建关系"}</DialogTitle>
        <div className="space-y-4">
          <div>
            <label className="mb-1.5 block text-xs text-[var(--yak-components-field-label)]">
              从（多端 · 当前模型）
            </label>
            <Input value={modelId} disabled />
          </div>
          <div>
            <label className="mb-1.5 block text-xs text-[var(--yak-components-field-label)]">
              到（一端 · 维度表）
            </label>
            {editing ? (
              <Input value={relation?.toModelName ?? ""} disabled />
            ) : (
              <>
                <Select
                  value={targetModelId || null}
                  onValueChange={(value) => setTargetModelId(value ?? "")}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="选择目标维度表" />
                  </SelectTrigger>
                  <SelectContent>
                    {dimCandidates.map((candidate) => (
                      <SelectItem key={candidate.id} value={candidate.id}>
                        {candidate.name}（{candidate.id}）
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {!editing && dimCandidates.length === 0 && (
                  <p className="mt-1 text-[11px] leading-4 text-[var(--yak-components-field-label)]">
                    未找到匹配的模型。目标模型仅限当前工作空间——如果模型在其他业务线的工作空间，请先切换工作空间；如果尚未创建，请先到「逻辑模型」新建。
                  </p>
                )}
              </>
            )}
          </div>
          <div>
            <label className="mb-1.5 block text-xs text-[var(--yak-components-field-label)]">
              关系名（留空自动生成）
            </label>
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={64}
              placeholder="自动：{从表}_to_{维表}"
            />
          </div>
          <div>
            <label className="mb-1.5 block text-xs text-[var(--yak-components-field-label)]">
              描述（可选）
            </label>
            <Input
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              maxLength={500}
            />
          </div>
          <div>
            <div className="mb-1.5 flex items-center justify-between">
              <label className="text-xs text-[var(--yak-components-field-label)]">
                推荐关联键（按标准匹配，最多 3 组）
              </label>
              <Button size="small" variant="ghost" onClick={loadProposals}>
                重新推荐
              </Button>
            </div>
            {(!proposals || proposals.length === 0) && (
              <p className="rounded-md border border-[var(--yak-components-panel-border)] p-2.5 text-xs leading-5 text-[var(--yak-components-field-label)]">
                {proposals === null
                  ? "选择目标维度表后自动推荐关联键。"
                  : "两侧没有可匹配的标准字段。请先在数据标准中补齐绑定，或手动添加键对（键字段必须已绑定标准）。"}
              </p>
            )}
            <div className="space-y-2">
              {(proposals ?? []).map((proposal) => {
                const selected = pairs.some(
                  (pair) =>
                    pair.fromFieldCode === proposal.fromFieldCode &&
                    pair.toFieldCode === proposal.toFieldCode,
                );
                return (
                  <button
                    key={`${proposal.fromFieldCode}->${proposal.toFieldCode}`}
                    type="button"
                    disabled={!proposal.selectable}
                    onClick={() =>
                      setPairs((previous) => [
                        ...previous,
                        {
                          fromFieldCode: proposal.fromFieldCode,
                          toFieldCode: proposal.toFieldCode,
                        },
                      ])
                    }
                    className={`w-full rounded-lg border p-2.5 text-left ${
                      proposal.selectable
                        ? "hover:border-[var(--yak-components-accent)]"
                        : "opacity-60"
                    } ${selected ? "border-[var(--yak-components-accent)] bg-[var(--yak-components-badge-info-bg)]" : "border-[var(--yak-components-panel-border)]"}`}
                  >
                    <p className="font-mono text-xs">
                      {proposal.fromFieldCode} → {proposal.toFieldCode}
                    </p>
                    <p className="mt-1 text-[11px] leading-4 text-[var(--yak-components-field-label)]">
                      {proposal.sameStandard ? "同标准" : "不同标准"} ·{" "}
                      {proposal.reason ?? (proposal.typesCompatible ? "类型一致" : "类型不一致")}
                    </p>
                  </button>
                );
              })}
            </div>
          </div>
          <div>
            <label className="mb-1.5 block text-xs text-[var(--yak-components-field-label)]">
              手动添加键对（两侧字段都必须已绑定标准）
            </label>
            <div className="flex items-center gap-2">
              <Input
                value={manualFrom}
                placeholder="事实表字段编码"
                onChange={(event) => setManualFrom(event.target.value)}
              />
              <span className="text-xs text-[var(--yak-components-field-label)]">=</span>
              <Input
                value={manualTo}
                placeholder="维表字段编码"
                onChange={(event) => setManualTo(event.target.value)}
              />
              <Button
                size="small"
                disabled={!manualFrom.trim() || !manualTo.trim()}
                onClick={() => {
                  const fromFieldCode = manualFrom.trim();
                  const toFieldCode = manualTo.trim();
                  if (
                    pairs.some(
                      (pair) =>
                        pair.fromFieldCode === fromFieldCode && pair.toFieldCode === toFieldCode,
                    )
                  ) {
                    setFailure("该键对已经在映射中");
                    return;
                  }
                  setPairs((previous) => [...previous, { fromFieldCode, toFieldCode }]);
                  setManualFrom("");
                  setManualTo("");
                  setFailure(null);
                }}
              >
                添加
              </Button>
            </div>
          </div>
          <div>
            <label className="mb-1.5 block text-xs text-[var(--yak-components-field-label)]">
              已选映射（有序，支持复合键）
            </label>
            {pairs.length === 0 && (
              <p className="text-xs text-[var(--yak-components-field-label)]">尚无键对。</p>
            )}
            <div className="space-y-2">
              {pairs.map((pair, index) => (
                <div key={`${pair.fromFieldCode}-${index}`} className="flex items-center gap-2">
                  <span className="flex-1 rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-surface-muted)] px-2.5 py-1.5 font-mono text-xs">
                    {pair.fromFieldCode}
                  </span>
                  <span className="text-[var(--yak-components-field-label)]">→</span>
                  <span className="flex-1 rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-surface-muted)] px-2.5 py-1.5 font-mono text-xs">
                    {pair.toFieldCode}
                  </span>
                  <Button
                    size="small"
                    variant="ghost"
                    onClick={() => setPairs((previous) => previous.filter((_, at) => at !== index))}
                  >
                    移除
                  </Button>
                </div>
              ))}
            </div>
          </div>
          <div>
            <label className="mb-1.5 block text-xs text-[var(--yak-components-field-label)]">
              Join 类型
            </label>
            <Select
              value={joinType || null}
              onValueChange={(value) => setJoinType(value ?? "LEFT")}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="LEFT">LEFT（维表缺失时保留事实行）</SelectItem>
                <SelectItem value="INNER">INNER</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {failure && (
            <p className="rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-badge-danger-bg)] p-2.5 text-xs text-[var(--yak-components-badge-danger-text)]">
              {failure}
            </p>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t border-[var(--yak-components-panel-border)] px-4 py-3">
          <Button onClick={onClose}>取消</Button>
          <Button variant="primary" disabled={busy} onClick={save}>
            {busy ? "保存中…" : "保存关系"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
