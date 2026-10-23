import {
  Alert,
  Badge,
  Button,
  Input,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type {
  ConceptDimension,
  DimensionFilter,
  DimensionScope,
} from "@/service/data-modeling/dimension-modeling/types";
import { DIMENSION_SCOPE_LABEL } from "@/service/data-modeling/dimension-modeling/types";
import { allDomainsOf, datamartsOf } from "../shared/planning-tree";
import DeleteDialog from "./delete-dialog";
import DimensionDialog from "./dimension-dialog";
import { useDimensionList, usePlanningTree } from "./state";

const SCOPE_FILTERS: { value: DimensionScope | "ALL"; label: string }[] = [
  { value: "ALL", label: "全部归属" },
  { value: "PUBLIC", label: "公有层" },
  { value: "APPLICATION", label: "应用层" },
];

const ALL = "ALL";

export default function ConceptDimensionsPage() {
  const options = usePlanningTree();
  const [scope, setScope] = useState<DimensionScope | "ALL">(ALL);
  const [scopeId, setScopeId] = useState(ALL);
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [version, setVersion] = useState(0);
  const [editing, setEditing] = useState<ConceptDimension | null>(null);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<ConceptDimension | null>(null);
  const filter = useMemo<DimensionFilter>(
    () => ({ scope, scopeId: scopeId === ALL ? "" : scopeId }),
    [scope, scopeId],
  );
  const list = useDimensionList(filter, version);
  const scopeItems = useMemo(() => {
    if (scope === "PUBLIC")
      return [
        { value: ALL, label: "全部数据域" },
        ...allDomainsOf(options.tree).map((domain) => ({
          value: domain.id,
          label: `${domain.categoryName} / ${domain.name}`,
        })),
      ];
    if (scope === "APPLICATION")
      return [
        { value: ALL, label: "全部数据集市" },
        ...datamartsOf(options.tree).map((datamart) => ({
          value: datamart.id,
          label: datamart.name,
        })),
      ];
    return [];
  }, [scope, options.tree]);
  const rows = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    const ordered = [...list.items].sort(
      (left, right) =>
        DIMENSION_SCOPE_LABEL[left.scope].localeCompare(DIMENSION_SCOPE_LABEL[right.scope]) ||
        left.name.localeCompare(right.name),
    );
    if (!text) return ordered;
    return ordered.filter((row) =>
      [row.name, row.enName, row.code].some((value) => value.toLowerCase().includes(text)),
    );
  }, [list.items, keyword]);
  useEffect(() => {
    const last = Math.max(1, Math.ceil(rows.length / pageSize));
    if (pageNo > last) setPageNo(last);
  }, [rows.length, pageSize, pageNo]);
  const pageRows = rows.slice((pageNo - 1) * pageSize, pageNo * pageSize);
  const columns: TableColumns<ConceptDimension> = [
    { title: "中文名", dataIndex: "name", width: 170, ellipsis: true },
    { title: "英文名", dataIndex: "enName", width: 190, ellipsis: true },
    {
      title: "英文缩写",
      key: "code",
      width: 150,
      ellipsis: true,
      render: (_, row) => <span className="font-mono text-[11px]">{row.code}</span>,
    },
    {
      title: "归属类型",
      key: "scope",
      width: 110,
      render: (_, row) => (
        <Badge tone={row.scope === "PUBLIC" ? "info" : "warning"}>
          {DIMENSION_SCOPE_LABEL[row.scope]}
        </Badge>
      ),
    },
    {
      title: "归属",
      key: "owner",
      width: 220,
      ellipsis: true,
      render: (_, row) => row.domainName ?? row.datamartName ?? "—",
    },
    {
      title: "描述",
      key: "description",
      width: 260,
      ellipsis: true,
      render: (_, row) => row.description ?? "—",
    },
    {
      title: "操作",
      key: "actions",
      width: 110,
      fixed: "right",
      align: "center",
      render: (_, row) => (
        <div className="flex items-center justify-center gap-2">
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
            onClick={() => setEditing(row)}
          >
            编辑
          </Button>
          <Button
            size="small"
            variant="ghost"
            className="px-0.5 font-normal text-[#667085] hover:text-[#d92d20]"
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
      <PageHeader
        title="概念维度"
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <div className="flex items-center gap-2">
            <Button
              size="small"
              disabled={list.loading}
              onClick={() => {
                options.reload();
                setVersion((value) => value + 1);
              }}
            >
              刷新
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={options.status !== "ready"}
              onClick={() => setCreating(true)}
            >
              <Plus size={14} />
              新建维度
            </Button>
          </div>
        }
      />
      <div className="flex min-h-0 flex-1 flex-col gap-3 px-6 pb-4 pt-5 max-md:px-4">
        {options.status === "failed" && (
          <Alert>
            业务分类与数据集市加载失败
            <Button size="small" onClick={options.reload}>
              重试
            </Button>
          </Alert>
        )}
        <div className="flex flex-wrap items-center gap-2">
          <div className="w-[136px]">
            <Select
              size="small"
              value={scope}
              items={SCOPE_FILTERS}
              onValueChange={(value) => {
                setScope((value ?? ALL) as DimensionScope | "ALL");
                setScopeId(ALL);
                setPageNo(1);
              }}
            >
              <SelectTrigger variant="outlined" aria-label="按归属类型筛选">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SCOPE_FILTERS.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    <SelectItemText>{item.label}</SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {scope !== ALL && (
            <div className="w-[240px]">
              <Select
                size="small"
                value={scopeId}
                items={scopeItems}
                onValueChange={(value) => {
                  setScopeId(value ?? ALL);
                  setPageNo(1);
                }}
              >
                <SelectTrigger variant="outlined" aria-label="按归属筛选">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {scopeItems.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      <SelectItemText>{item.label}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <form
            className="flex max-w-full items-center gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              setKeyword(searchText.trim());
              setPageNo(1);
            }}
          >
            <Input
              size="small"
              variant="outlined"
              value={searchText}
              maxLength={128}
              aria-label="搜索维度名称或缩写"
              placeholder="名称、英文名或缩写"
              onChange={(event) => setSearchText(event.target.value)}
            />
            <Button size="small" type="submit">
              搜索
            </Button>
            {(searchText || keyword) && (
              <Button
                size="small"
                onClick={() => {
                  setSearchText("");
                  setKeyword("");
                  setPageNo(1);
                }}
              >
                清空
              </Button>
            )}
          </form>
        </div>
        {list.failed && (
          <Alert>
            概念维度加载失败
            <Button size="small" onClick={() => setVersion((value) => value + 1)}>
              重试
            </Button>
          </Alert>
        )}
        <div className="min-h-0 min-w-0 flex-1 overflow-auto">
          <Table<ConceptDimension>
            rowKey="id"
            columns={columns}
            dataSource={list.failed ? [] : pageRows}
            loading={list.loading || options.status === "loading"}
            size="small"
            bordered={false}
            scroll={{ x: 1210 }}
            emptyText="暂无概念维度"
            pagination={{
              current: pageNo,
              pageSize,
              total: rows.length,
              disabled: list.loading,
              showSizeChanger: true,
              pageSizeOptions: [20, 50, 100],
              onChange: (page, size) => {
                setPageSize(size);
                setPageNo(size === pageSize ? page : 1);
              },
            }}
          />
        </div>
      </div>
      {(creating || editing) && (
        <DimensionDialog
          existing={editing}
          tree={options.tree}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSaved={() => {
            setCreating(false);
            setEditing(null);
            setVersion((value) => value + 1);
          }}
        />
      )}
      {deleting && (
        <DeleteDialog
          dimension={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            setDeleting(null);
            setVersion((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}
