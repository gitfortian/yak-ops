import { Alert, Button, PageHeader, Tabs, TabsList, TabsTab, toast } from "@yak-ops/yak-ui";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Clock, Star, Users } from "lucide-react";
import { useEffect, type ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { countLabel, searchTables } from "@/service/data-map/catalog";
import { listFavorites, listRecent, removeFavorite } from "@/service/data-map/personal";
import { dataMapFailureMessage } from "@/service/data-map/request";

import AssetTable from "../shared/asset-table";
import DataMapEmptyState from "../shared/empty-state";
import { dataMapKeys } from "../shared/keys";
import { DATA_MAP_BASE } from "../shared/labels";
import { useDataMapScope } from "../shared/scope";

const DEFAULT_PAGE_SIZE = 20;

type PersonalTab = "favorites" | "recent" | "owned";

const readTab = (value: string | null): PersonalTab => {
  if (value === "recent") return "recent";
  if (value === "owned") return "owned";
  return "favorites";
};

const StatCard = ({
  icon,
  value,
  label,
  active,
  onClick,
}: {
  icon: ReactNode;
  value: string;
  label: string;
  active: boolean;
  onClick: () => void;
}) => (
  <button
    type="button"
    onClick={onClick}
    className={`flex cursor-pointer items-center gap-3 rounded-[var(--yak-radius-control-medium)] border p-4 text-left transition-colors ${
      active
        ? "border-[var(--yak-color-primary)] bg-[var(--yak-color-primary-subtle)]"
        : "border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] hover:border-[var(--yak-color-primary)]"
    }`}
  >
    <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
      {icon}
    </span>
    <span className="min-w-0">
      <span className="block text-xl font-semibold text-[var(--yak-components-panel-text)]">
        {value}{" "}
        <span className="text-xs font-normal text-[var(--yak-components-muted-text)]">{label}</span>
      </span>
    </span>
  </button>
);

export default function PersonalPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const tab = readTab(searchParams.get("tab"));
  const pageNo = Math.max(1, Number(searchParams.get("pageNo")) || 1);
  const { scope, ready } = useDataMapScope();

  const write = (next: { tab: PersonalTab; pageNo: number }) => {
    const params = new URLSearchParams();
    if (next.tab !== "favorites") params.set("tab", next.tab);
    if (next.pageNo > 1) params.set("pageNo", String(next.pageNo));
    setSearchParams(params, { replace: true });
  };

  const stats = useQuery({
    queryKey: [...dataMapKeys.all, "personal-stats", ...scope] as const,
    queryFn: async ({ signal }) => {
      const [favorites, recent, owned] = await Promise.all([
        listFavorites(1, 1, signal),
        listRecent(1, 1, signal),
        searchTables({ ownerId: scope[0], pageNo: 1, pageSize: 1 }, signal),
      ]);
      return {
        favorites: favorites.pagination.total,
        recent: recent.pagination.total,
        owned: owned.pagination.total,
      };
    },
    enabled: ready,
  });

  const ownedPage = useQuery({
    queryKey: dataMapKeys.tables(scope, {
      ownerId: scope[0],
      pageNo,
      pageSize: DEFAULT_PAGE_SIZE,
    }),
    queryFn: ({ signal }) =>
      searchTables({ ownerId: scope[0], pageNo, pageSize: DEFAULT_PAGE_SIZE }, signal),
    enabled: ready && tab === "owned",
    placeholderData: keepPreviousData,
  });

  const personalPage = useQuery({
    queryKey:
      tab === "favorites"
        ? dataMapKeys.favorites(scope, pageNo, DEFAULT_PAGE_SIZE)
        : dataMapKeys.recent(scope, pageNo, DEFAULT_PAGE_SIZE),
    queryFn: ({ signal }) =>
      tab === "favorites"
        ? listFavorites(pageNo, DEFAULT_PAGE_SIZE, signal)
        : listRecent(pageNo, DEFAULT_PAGE_SIZE, signal),
    enabled: ready && tab !== "owned",
    placeholderData: keepPreviousData,
  });

  const removeFavoriteMutation = useMutation({
    mutationFn: (tableId: string) => removeFavorite(tableId),
    onSuccess: () => {
      toast.success("已取消收藏");
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "favorites"] });
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "personal-stats"] });
    },
    onError: () => toast.error("取消收藏失败，请稍后重试"),
  });

  const activePage = tab === "owned" ? ownedPage : personalPage;
  const records = activePage.data?.bizData ?? [];
  const total = activePage.data?.pagination?.total ?? 0;
  /**
   * Empty is decided by the page contents, not by the count alone: favourites and visits keep
   * rows whose asset is gone, so a page can be empty while the total is not.
   */
  const isEmpty = activePage.isSuccess && !activePage.isPlaceholderData && records.length === 0;

  useEffect(() => {
    if (!activePage.isSuccess || activePage.isPlaceholderData) return;
    if (records.length === 0 && total > 0 && pageNo > 1) {
      const next = new URLSearchParams();
      if (tab !== "favorites") next.set("tab", tab);
      if (pageNo - 1 > 1) next.set("pageNo", String(pageNo - 1));
      setSearchParams(next, { replace: true });
    }
  }, [
    activePage.isSuccess,
    activePage.isPlaceholderData,
    records.length,
    total,
    pageNo,
    tab,
    setSearchParams,
  ]);

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--yak-color-background)]">
      <PageHeader
        title="我的数据"
        description="你的收藏、最近访问与负责的资产。"
        className="px-6 pb-5 pt-5 max-md:px-4"
      />

      <div className="flex min-h-0 flex-1 flex-col overflow-auto px-6 pb-4 max-md:px-4">
        <div className="grid shrink-0 grid-cols-1 gap-3 md:grid-cols-3">
          <StatCard
            icon={<Star size={16} aria-hidden="true" />}
            value={stats.data ? countLabel(stats.data.favorites) : "—"}
            label="个收藏"
            active={tab === "favorites"}
            onClick={() => write({ tab: "favorites", pageNo: 1 })}
          />
          <StatCard
            icon={<Clock size={16} aria-hidden="true" />}
            value={stats.data ? countLabel(stats.data.recent) : "—"}
            label="条最近访问"
            active={tab === "recent"}
            onClick={() => write({ tab: "recent", pageNo: 1 })}
          />
          <StatCard
            icon={<Users size={16} aria-hidden="true" />}
            value={stats.data ? countLabel(stats.data.owned) : "—"}
            label="我负责的资产"
            active={tab === "owned"}
            onClick={() => write({ tab: "owned", pageNo: 1 })}
          />
        </div>

        <div className="mt-4 flex min-h-0 flex-1 flex-col rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
          <Tabs
            value={tab}
            onValueChange={(value) => write({ tab: readTab(value as string), pageNo: 1 })}
          >
            <TabsList>
              <TabsTab value="favorites">我的收藏</TabsTab>
              <TabsTab value="recent">最近访问</TabsTab>
              <TabsTab value="owned">我负责的资产</TabsTab>
            </TabsList>
          </Tabs>

          <div className="mt-3 flex items-center justify-between gap-2">
            <span className="text-xs text-[var(--yak-components-muted-text)]">
              共 {countLabel(total)} 个资产
            </span>
            {activePage.isError && (
              <Button size="small" onClick={() => void activePage.refetch()}>
                重试
              </Button>
            )}
          </div>

          {activePage.isError && (
            <Alert className="mt-2">
              {dataMapFailureMessage(activePage.error, "加载失败，请稍后重试。")}
            </Alert>
          )}

          <div className="mt-2 min-h-0 flex-1 overflow-auto">
            {isEmpty ? (
              <DataMapEmptyState
                title={
                  tab === "favorites"
                    ? "还没有收藏任何资产"
                    : tab === "recent"
                      ? "还没有访问过资产"
                      : "还没有负责的资产"
                }
                description={
                  tab === "favorites"
                    ? "在资产详情页点击收藏，常用的表会集中出现在这里。"
                    : tab === "recent"
                      ? "打开任意资产详情后，最近访问会按时间倒序记录在这里。"
                      : "在资产详情页把负责人指派给自己后，资产会集中出现在这里。"
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
                loading={activePage.isFetching}
                favoriteAction={
                  tab === "favorites"
                    ? (record) => (
                        <button
                          type="button"
                          title="取消收藏"
                          aria-label={`取消收藏 ${record.name}`}
                          onClick={() => removeFavoriteMutation.mutate(record.id)}
                          className="cursor-pointer text-[var(--yak-components-badge-warning-text)]"
                        >
                          <Star size={15} fill="currentColor" aria-hidden="true" />
                        </button>
                      )
                    : undefined
                }
                pagination={{
                  pageNo,
                  pageSize: DEFAULT_PAGE_SIZE,
                  total,
                  onPageChange: (nextPage) => write({ tab, pageNo: nextPage }),
                }}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
