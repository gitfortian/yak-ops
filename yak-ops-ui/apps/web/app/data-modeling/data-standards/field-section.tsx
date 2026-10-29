import { Alert, Button } from "@yak-ops/yak-ui";
import { useEffect, useMemo, useState } from "react";
import type {
  DeleteTarget,
  StandardDraft,
  StandardQuery,
} from "@/service/data-modeling/data-standards/types";
import KindToolbar from "./kind-toolbar";
import StandardDialog from "./standard-dialog";
import StandardTable from "./standard-table";
import { useStandardPage } from "./state";

interface Props {
  selectedId: string | null;
  selectedPath: string;
  unavailable: boolean;
  /** True when the selected node is a standard set that holds the other kind. */
  kindBlocked: boolean;
  version: number;
  onChanged: () => void;
  onDelete: (target: DeleteTarget) => void;
  onCatalogStale: () => void;
}

export default function FieldSection(props: Props) {
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [draft, setDraft] = useState<StandardDraft | null>(null);
  const query = useMemo<StandardQuery | null>(() => {
    if (props.unavailable) return null;
    if (keyword) return { scope: "ALL", keyword, pageNo, pageSize };
    if (props.selectedId)
      return { scope: "CATEGORY", categoryId: props.selectedId, pageNo, pageSize };
    return null;
  }, [props.unavailable, keyword, pageNo, pageSize, props.selectedId]);
  const list = useStandardPage(query, props.version);
  useEffect(() => {
    // A new category is a new result set: keeping the old page number would request a page that
    // does not exist yet and briefly show an empty table.
    setPageNo(1);
  }, [props.selectedId]);
  useEffect(() => {
    if (list.page && !list.loading) {
      const last = Math.max(1, list.page.pagination.pages);
      if (pageNo > last) setPageNo(last);
    }
  }, [list.page, list.loading, pageNo]);
  const clearSearch = () => {
    setSearchText("");
    setKeyword("");
    setPageNo(1);
  };
  return (
    <>
      <KindToolbar
        disabled={props.unavailable}
        searching={Boolean(keyword)}
        searchText={searchText}
        onSearchTextChange={setSearchText}
        onSearch={() => {
          setKeyword(searchText.trim());
          setPageNo(1);
        }}
        onClear={clearSearch}
        createLabel="新建标准"
        createDisabled={!props.selectedId || props.kindBlocked}
        hint={
          props.kindBlocked ? (
            <span className="text-xs text-[var(--yak-color-text-secondary)]">
              当前标准集存放标准代码
            </span>
          ) : null
        }
        onCreate={() => {
          if (props.selectedId)
            setDraft({
              mode: "create",
              category: { id: props.selectedId, path: props.selectedPath },
            });
        }}
      />
      {list.failed && (
        <Alert>
          字段标准加载失败
          <Button size="small" onClick={props.onChanged}>
            重试
          </Button>
        </Alert>
      )}
      <div className="min-h-0 min-w-0 flex-1 overflow-auto">
        <StandardTable
          page={list.failed ? null : list.page}
          pageNo={pageNo}
          pageSize={pageSize}
          // An unavailable catalogue holds no query, so the hook is idle: showing its spinner
          // through the unavailable flag left the table loading forever after a failed load.
          loading={list.loading}
          searching={Boolean(keyword)}
          onEdit={(standard) => setDraft({ mode: "edit", standard })}
          onDelete={(node) => props.onDelete({ kind: "field", node })}
          onPageChange={(page, size) => {
            setPageSize(size);
            setPageNo(size === pageSize ? page : 1);
          }}
        />
      </div>
      {draft && (
        <StandardDialog
          draft={draft}
          onClose={() => setDraft(null)}
          onSaved={() => {
            setDraft(null);
            props.onChanged();
          }}
          onStaleCategory={() => {
            props.onCatalogStale();
            props.onChanged();
          }}
        />
      )}
    </>
  );
}
