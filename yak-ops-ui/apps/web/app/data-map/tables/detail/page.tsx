import { Alert, Button, PageHeader, Tabs, TabsList, TabsTab } from "@yak-ops/yak-ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { getTable } from "@/service/data-map/catalog";
import { addFavorite, recordView, removeFavorite } from "@/service/data-map/personal";
import { dataMapFailureMessage } from "@/service/data-map/request";

import { dataMapKeys } from "../../shared/keys";
import { DATA_MAP_BASE, locationLabel } from "../../shared/labels";
import { useDataMapScope } from "../../shared/scope";
import BasicsTab from "./basics-tab";
import ChangesTab from "./changes-tab";
import ColumnsTab from "./columns-tab";
import PreviewTab from "./preview-tab";

const TABS = ["basics", "columns", "preview", "changes"] as const;
type DetailTab = (typeof TABS)[number];

const readTab = (value: string | null): DetailTab => TABS.find((tab) => tab === value) ?? "basics";

export default function TableDetailPage() {
  const { tableId = "" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = readTab(searchParams.get("tab"));
  const { scope, ready } = useDataMapScope();

  const detail = useQuery({
    queryKey: dataMapKeys.table(scope, tableId),
    queryFn: ({ signal }) => getTable(tableId, signal),
    enabled: ready && Boolean(tableId),
  });

  const recorded = useRef("");
  const loaded = detail.isSuccess;
  useEffect(() => {
    if (!loaded || recorded.current === tableId) return;
    recorded.current = tableId;
    void recordView(tableId).then(() => {
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "recent"] });
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "overview"] });
    });
  }, [loaded, tableId, queryClient]);

  const favorite = useMutation({
    mutationFn: (next: boolean) => (next ? addFavorite(tableId) : removeFavorite(tableId)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: dataMapKeys.table(scope, tableId) });
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "favorites"] });
    },
  });

  const changeTab = (value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value === "basics") next.delete("tab");
    else next.set("tab", value);
    setSearchParams(next, { replace: true });
  };

  const record = detail.data;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title={record?.name ?? "资产详情"}
        description={
          record
            ? [
                record.datasourceName ?? record.datasourceId,
                locationLabel(record.database, record.schema),
              ]
                .filter(Boolean)
                .join(" · ")
            : undefined
        }
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <>
            <Button
              size="small"
              variant="ghost"
              onClick={() => navigate(`${DATA_MAP_BASE}/catalog`)}
            >
              返回目录
            </Button>
            {record && (
              <Button
                size="small"
                loading={favorite.isPending}
                onClick={() => favorite.mutate(!record.favorited)}
              >
                {record.favorited ? "取消收藏" : "收藏"}
              </Button>
            )}
          </>
        }
      />

      <div className="flex min-h-0 flex-1 flex-col px-6 pb-4 pt-5 max-md:px-4">
        {detail.isError && (
          <Alert>
            {dataMapFailureMessage(detail.error, "资产加载失败，请稍后重试。")}
            <Button size="small" className="ml-3" onClick={() => void detail.refetch()}>
              重试
            </Button>
          </Alert>
        )}

        {record?.status === "MISSING" && (
          <Alert className="mb-4">
            该表已不在其数据源中，通常意味着被删除或重命名。重命名后的新表会作为新资产出现，描述与负责人需要手工复制。
          </Alert>
        )}

        {record && (
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <Tabs value={tab} onValueChange={(value) => changeTab(value as string)}>
              <TabsList>
                <TabsTab value="basics">基础信息</TabsTab>
                <TabsTab value="columns">字段</TabsTab>
                <TabsTab value="preview">预览</TabsTab>
                <TabsTab value="changes">变更记录</TabsTab>
              </TabsList>
            </Tabs>

            <div className="mt-4 min-h-0 flex-1 overflow-auto">
              {tab === "basics" && (
                <BasicsTab detail={record} onSaved={() => void detail.refetch()} />
              )}
              {tab === "columns" && <ColumnsTab columns={record.columns} />}
              {tab === "preview" && <PreviewTab tableId={record.id} />}
              {tab === "changes" && <ChangesTab tableId={record.id} />}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
