import { Alert, Button, PageHeader, Tabs, TabsList, TabsTab } from "@yak-ops/yak-ui";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";

import { countLabel } from "@/service/data-map/catalog";
import { listFavorites, listRecent } from "@/service/data-map/personal";
import { dataMapFailureMessage } from "@/service/data-map/request";

import AssetTable from "../shared/asset-table";
import { dataMapKeys } from "../shared/keys";
import { useDataMapScope } from "../shared/scope";

const DEFAULT_PAGE_SIZE = 20;

type PersonalTab = "favorites" | "recent";

const readTab = (value: string | null): PersonalTab =>
  value === "recent" ? "recent" : "favorites";

export default function PersonalPage() {
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

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader title="我的数据" bordered className="bg-white px-6 max-md:px-4" />

      <div className="flex min-h-0 flex-1 flex-col px-6 pb-4 pt-5 max-md:px-4">
        <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
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
            <AssetTable
              records={records}
              loading={page.isFetching}
              emptyText={tab === "favorites" ? "还没有收藏任何资产" : "还没有访问过资产"}
              pagination={{
                pageNo,
                pageSize: DEFAULT_PAGE_SIZE,
                total,
                onPageChange: (nextPage) => write({ tab, pageNo: nextPage }),
              }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
