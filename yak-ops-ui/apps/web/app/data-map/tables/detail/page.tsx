import { Alert, Button, Card, Spinner, Tabs, TabsList, TabsTab } from "@yak-ops/yak-ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";

import { getTable } from "@/service/data-map/catalog";
import { addFavorite, recordView, removeFavorite } from "@/service/data-map/personal";
import { dataMapFailureMessage } from "@/service/data-map/request";

import { dataMapKeys } from "../../shared/keys";
import { useDataMapScope } from "../../shared/scope";
import BasicsTab from "./basics-tab";
import ChangesTab from "./changes-tab";
import ColumnsTab from "./columns-tab";
import DetailHeader from "./detail-header";
import PreviewTab from "./preview-tab";

const TABS = ["basics", "columns", "preview", "changes"] as const;
type DetailTab = (typeof TABS)[number];

const readTab = (value: string | null): DetailTab => TABS.find((tab) => tab === value) ?? "basics";

export default function TableDetailPage() {
  const { tableId = "" } = useParams();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = readTab(searchParams.get("tab"));
  const { scope, ready } = useDataMapScope();
  const [editing, setEditing] = useState(false);

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

  const record = detail.data;
  /** A missing asset has no live rows, so sampling it would only fail; the tab is not offered. */
  const previewDisabled = record?.status === "MISSING";

  const changeTab = (value: string) => {
    if (editing) setEditing(false);
    const next = new URLSearchParams(searchParams);
    if (value === "basics") next.delete("tab");
    else next.set("tab", value);
    setSearchParams(next, { replace: true });
  };

  useEffect(() => {
    if (previewDisabled && tab === "preview") changeTab("basics");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [previewDisabled, tab]);

  return (
    <div className="h-full min-h-0 overflow-auto bg-[var(--yak-datamap-page-bg)]">
      <div className="mx-auto w-full max-w-[1180px] px-6 py-5 max-md:px-4">
        {detail.isError && (
          <Alert className="mb-4">
            {dataMapFailureMessage(detail.error, "资产加载失败，请稍后重试。")}
            <Button size="small" className="ml-3" onClick={() => void detail.refetch()}>
              重试
            </Button>
          </Alert>
        )}

        {detail.isPending && (
          <div className="flex items-center gap-2 py-16 text-xs text-[var(--yak-components-muted-text)]">
            <Spinner size="small" />
            正在加载资产
          </div>
        )}

        {record && (
          <>
            <DetailHeader
              detail={record}
              favoritePending={favorite.isPending}
              onEdit={() => {
                setEditing(true);
                if (tab !== "basics") changeTab("basics");
              }}
              onToggleFavorite={() => favorite.mutate(!record.favorited)}
            />

            <Card className="mt-4 shadow-[var(--yak-datamap-card-shadow)]">
              <div className="px-5 pt-3.5 max-md:px-4">
                <Tabs value={tab} onValueChange={(value) => changeTab(value as string)}>
                  <TabsList>
                    <TabsTab value="basics">基础信息</TabsTab>
                    <TabsTab value="columns">字段</TabsTab>
                    <TabsTab value="preview" disabled={previewDisabled}>
                      数据预览
                    </TabsTab>
                    <TabsTab value="changes">变更记录</TabsTab>
                  </TabsList>
                </Tabs>
              </div>

              <div className="px-5 py-5 max-md:px-4">
                {tab === "basics" && (
                  <BasicsTab
                    detail={record}
                    editing={editing}
                    onCancel={() => setEditing(false)}
                    onSaved={() => {
                      setEditing(false);
                      void detail.refetch();
                    }}
                  />
                )}
                {tab === "columns" && <ColumnsTab columns={record.columns} />}
                {tab === "preview" && <PreviewTab tableId={record.id} />}
                {tab === "changes" && <ChangesTab tableId={record.id} />}
              </div>
            </Card>
          </>
        )}
      </div>
    </div>
  );
}
