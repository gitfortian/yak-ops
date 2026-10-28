import { Alert, Button, Spinner, Switch, Table, toast, type TableColumns } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import {
  getLayerDataSources,
  getLayers,
  layerFailureMessage,
  saveLayer,
  type Layer,
  type LayerDataSource,
} from "@/service/data-modeling/warehouse-planning/layers";
import LayerDeleteDialog from "./layer-delete-dialog";
import LayerDialog from "./layer-dialog";
import NamingRuleDialog from "./naming-rule-dialog";

export default function LayerPanel() {
  const [layers, setLayers] = useState<Layer[]>([]);
  const [dataSources, setDataSources] = useState<LayerDataSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [version, setVersion] = useState(0);
  const [draft, setDraft] = useState<Layer | null | undefined>(undefined);
  const [deleting, setDeleting] = useState<Layer | null>(null);
  const [naming, setNaming] = useState<Layer | null>(null);
  const [toggling, setToggling] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setFailed(false);
    void Promise.all([getLayers(controller.signal), getLayerDataSources(controller.signal)])
      .then(([rows, sources]) => {
        if (!active) return;
        setLayers(rows);
        setDataSources(sources);
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
  }, [version]);

  const refresh = () => setVersion((value) => value + 1);

  const toggle = async (row: Layer, enabled: boolean) => {
    if (enabled && !row.dataSourceId) {
      toast.error("请先绑定物理数据源再启用");
      return;
    }
    setToggling(row.id);
    try {
      const saved = await saveLayer(row, {
        cnName: row.cnName,
        enCode: row.enCode,
        dataSourceId: row.dataSourceId,
        description: row.description,
        enabled,
      });
      setLayers((previous) => previous.map((item) => (item.id === saved.id ? saved : item)));
    } catch (error) {
      toast.error(layerFailureMessage(error));
      refresh();
    } finally {
      setToggling(null);
    }
  };

  const columns: TableColumns<Layer> = [
    {
      title: "分层中文名",
      key: "cnName",
      width: 180,
      render: (_, row) => (
        <span>
          {row.cnName}
          {row.builtin && (
            <span className="ml-1 text-xs text-[var(--yak-color-text-secondary)]">（默认）</span>
          )}
        </span>
      ),
    },
    { title: "英文缩写", dataIndex: "enCode", width: 120 },
    {
      title: "物理数据源",
      key: "dataSource",
      width: 200,
      render: (_, row) =>
        row.dataSourceName ?? (
          <span className="text-[var(--yak-components-muted-text)]">未绑定</span>
        ),
    },
    {
      title: "描述",
      dataIndex: "description",
      ellipsis: true,
      render: (_, row) => row.description ?? "—",
    },
    {
      title: "状态",
      key: "enabled",
      width: 130,
      render: (_, row) => (
        <label className="flex items-center gap-2">
          <Switch
            size="small"
            aria-label={`${row.enabled ? "禁用" : "启用"}分层：${row.cnName}`}
            checked={row.enabled}
            loading={toggling === row.id}
            disabled={loading || toggling !== null}
            onCheckedChange={(checked) => void toggle(row, checked)}
          />
          <span className="text-xs text-[var(--yak-color-text-secondary)]">
            {row.enabled ? "启用" : "禁用"}
          </span>
        </label>
      ),
    },
    {
      title: "命名规则",
      key: "naming",
      width: 100,
      render: (_, row) => (
        <span className="text-xs text-[var(--yak-color-text-secondary)]">
          {row.namingRuleEnabled ? "已启用" : "未启用"}
        </span>
      ),
    },
    {
      title: "操作",
      key: "actions",
      width: 190,
      fixed: "right",
      align: "center",
      render: (_, row) => (
        <div className="flex items-center justify-center gap-2">
          <Button
            size="small"
            variant="ghost"
            disabled={loading}
            className="px-0.5 font-normal text-[var(--yak-color-text-secondary)]"
            onClick={() => setNaming(row)}
          >
            命名规则
          </Button>
          <Button
            size="small"
            variant="ghost"
            disabled={loading}
            className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
            onClick={() => setDraft(row)}
          >
            编辑
          </Button>
          <span
            aria-hidden="true"
            className="h-3 w-px shrink-0 bg-[var(--yak-components-panel-border)]"
          />
          <Button
            size="small"
            variant="ghost"
            disabled={loading || row.builtin}
            title={row.builtin ? "默认分层不允许删除" : undefined}
            className="px-0.5 font-normal text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-components-danger-text)]"
            onClick={() => setDeleting(row)}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="mb-3 flex items-start justify-between gap-4">
        <div>
          <h2 className="text-sm font-medium">数仓分层</h2>
          <p className="mt-1 text-xs text-[var(--yak-color-text-secondary)]">
            维度建模只可选用启用状态的分层；物理发布时按分层绑定的数据源执行。
          </p>
        </div>
        <Button
          size="small"
          variant="primary"
          disabled={loading || failed}
          onClick={() => setDraft(null)}
        >
          新建分层
        </Button>
      </div>
      {failed ? (
        <Alert>
          数仓分层加载失败
          <Button size="small" onClick={refresh}>
            重试
          </Button>
        </Alert>
      ) : loading && layers.length === 0 ? (
        <Spinner />
      ) : (
        <div className="min-h-0 flex-1 overflow-auto">
          <Table<Layer>
            rowKey="id"
            columns={columns}
            dataSource={layers}
            loading={loading}
            size="small"
            bordered={false}
            scroll={{ x: 1250 }}
            emptyText="暂无分层"
            pagination={false}
          />
        </div>
      )}
      {draft !== undefined && (
        <LayerDialog
          layer={draft}
          dataSources={dataSources}
          onClose={() => setDraft(undefined)}
          onSaved={() => {
            setDraft(undefined);
            refresh();
          }}
        />
      )}
      {deleting && (
        <LayerDeleteDialog
          layer={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            setDeleting(null);
            refresh();
          }}
        />
      )}
      {naming && (
        <NamingRuleDialog
          layer={naming}
          onClose={() => setNaming(null)}
          onSaved={() => {
            setNaming(null);
            refresh();
          }}
        />
      )}
    </div>
  );
}
