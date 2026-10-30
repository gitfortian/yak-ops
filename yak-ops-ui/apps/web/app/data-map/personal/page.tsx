import { Alert, Button, Card, PageHeader, Tabs, TabsList, TabsTab } from "@yak-ops/yak-ui";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { countLabel } from "@/service/data-map/catalog";
import { listFavorites, listRecent } from "@/service/data-map/personal";
import { dataMapFailureMessage } from "@/service/data-map/request";

import AssetTable from "../shared/asset-table";
import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE } from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

const DEFAULT_PAGE_SIZE = 20;

type PersonalTab = "favorites" | "recent";

const readTab = (value: string | null): PersonalTab =>
  value === "recent" ? "recent" : "favorites";

export default function PersonalPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = readTab(searchParams.get("tab"));
  const pageNo = Math.max(1, Number(searchParams.get("pageNo")) || 1);
  const { scope, ready } = useDataMapScope();

  const write = (next: { tab: PersonalTab; pageNo: number }) => {
    const params = new URLSearchParams();
    if (next.tab === "recent") params.set("tab", "recent");
    if (next.pageNo > 1) params.set("pageNo", String(next.pageNo));
    setSearchParams(params, { replace: true });
  };

  const page = useQuery({
    queryKey:
      tab === "favorites"
        ? dataMapKeys.favorites(scope, pageNo, DEFAULT_PAGE_SIZE)
        : dataMapKeys.recent(scope, pageNo, DEFAULT_PAGE_SIZE),
    queryFn: ({ signal }) =>
      tab === "favorites"
        ? listFavorites(pageNo, DEFAULT_PAGE_SIZE, signal)
        : listRecent(pageNo, DEFAULT_PAGE_SIZE, signal),
    enabled: ready,
    placeholderData: keepPreviousData,
  });

  const records = page.data?.bizData ?? [];
  const total = page.data?.pagination?.total ?? 0;
  /**
   * Empty is decided by the page contents, not by the count alone: favourites and visits keep
   * rows whose asset is gone, so a page can be empty while the total is not.
   */
  const isEmpty = page.isSuccess && !page.isPlaceholderData && records.length === 0;

  useEffect(() => {
    if (!page.isSuccess || page.isPlaceholderData) return;
    if (records.length === 0 && total > 0 && pageNo > 1) {
      const next = new URLSearchParams();
      if (tab === "recent") next.set("tab", "recent");
      if (pageNo - 1 > 1) next.set("pageNo", String(pageNo - 1));
      setSearchParams(next, { replace: true });
    }
  }, [page.isSuccess, page.isPlaceholderData, records.length, total, pageNo, tab, setSearchParams]);

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--yak-color-background)]">
      <PageHeader
        title="我的数据"
        description="你收藏与最近访问过的数据资产。"
        className="px-6 pb-5 pt-5 max-md:px-4"
      />

      <div className="flex min-h-0 flex-1 flex-col px-6 pb-4 max-md:px-4">
        <Card className="flex min-h-0 flex-1 flex-col p-4">
          <Tabs
            value={tab}
            onValueChange={(value) => write({ tab: readTab(value as string), pageNo: 1 })}
          >
            <TabsList>
              <TabsTab value="favorites">我的收藏</TabsTab>
              <TabsTab value="recent">最近访问</TabsTab>
            </TabsList>
          </Tabs>

          <div className="mt-3 flex items-center justify-between gap-2">
            <span className="text-xs text-[var(--yak-components-muted-text)]">
              共 {countLabel(total)} 个资产
            </span>
            {page.isError && (
              <Button size="small" onClick={() => void page.refetch()}>
                重试
              </Button>
            )}
          </div>

          {page.isError && (
            <Alert className="mt-2">
              {dataMapFailureMessage(page.error, "加载失败，请稍后重试。")}
            </Alert>
          )}

          <div className="mt-2 min-h-0 flex-1 overflow-auto">
            {isEmpty ? (
              <DataMapEmptyState
                title={tab === "favorites" ? "还没有收藏任何资产" : "还没有访问过资产"}
                description={
                  tab === "favorites"
                    ? "在资产详情页点击收藏，常用的表会集中出现在这里。"
                    : "打开任意资产详情后，最近访问会按时间倒序记录在这里。"
                }
                action={
                  <Button
                    size="small"
                    variant="primary"
                    onClick={() => navigate(`${DATA_MAP_BASE}/catalog`)}
                  >
                    去资产目录
                  </Button>
                }
              />
            ) : (
              <AssetTable
                records={records}
                loading={page.isFetching}
                pagination={{
                  pageNo,
                  pageSize: DEFAULT_PAGE_SIZE,
                  total,
                  onPageChange: (nextPage) => write({ tab, pageNo: nextPage }),
                }}
              />
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}
