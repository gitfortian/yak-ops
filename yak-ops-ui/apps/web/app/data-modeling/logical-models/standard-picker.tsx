import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogTitle,
  Input,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import { getStandards } from "@/service/data-modeling/data-standards";
import type { StandardField } from "@/service/data-modeling/data-standards/types";
import type { StandardBindingWarning } from "./standard-binding";

interface Props {
  mode: "single" | "multiple";
  /** Codes already present in the model; they cannot be imported twice. */
  takenCodes: string[];
  onClose: () => void;
  onPick: (standards: StandardField[]) => void;
  /** Explains why this standard would detach the target row from its source column. */
  bindingWarning?: (standard: StandardField) => StandardBindingWarning | null;
}

const PAGE_SIZE = 20;

export default function StandardPicker({
  mode,
  takenCodes,
  onClose,
  onPick,
  bindingWarning,
}: Props) {
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<Awaited<ReturnType<typeof getStandards>> | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [version, setVersion] = useState(0);
  const [selected, setSelected] = useState<StandardField[]>([]);
  const [pending, setPending] = useState<{
    standard: StandardField;
    warning: StandardBindingWarning;
  } | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setFailed(false);
    void getStandards({ scope: "ALL", keyword, pageNo, pageSize: PAGE_SIZE }, controller.signal)
      .then((result) => {
        if (active) {
          setPage(result);
          setLoading(false);
        }
      })
      .catch(() => {
        if (active) {
          setPage(null);
          setLoading(false);
          setFailed(true);
        }
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [keyword, pageNo, version]);
  const taken = new Set(takenCodes);
  const selectedIds = new Set(selected.map((item) => item.id));
  const rows = failed ? [] : (page?.bizData ?? []);
  const blockedCount = rows.filter((row) => taken.has(row.enCode)).length;
  const toggle = (row: StandardField) => {
    if (mode === "single") {
      const warning = bindingWarning?.(row) ?? null;
      if (warning) {
        setPending({ standard: row, warning });
        return;
      }
      onPick([row]);
      return;
    }
    setSelected((previous) =>
      previous.some((item) => item.id === row.id)
        ? previous.filter((item) => item.id !== row.id)
        : [...previous, row],
    );
  };
  // Dialog content is 840px wide. Columns that hold free text share whatever the short, fixed
  // columns leave over, so the table fills the dialog instead of overflowing it.
  const columns: TableColumns<StandardField> = [
    { title: "中文名称", dataIndex: "cnName", minWidth: 168, ellipsis: true },
    { title: "英文编码", dataIndex: "enCode", minWidth: 180, ellipsis: true },
    { title: "数据类型", dataIndex: "dataType", width: 96 },
    {
      title: "长度/精度",
      key: "precision",
      width: 92,
      render: (_, row) => row.lengthPrecision ?? "—",
    },
    {
      title: "所属分类",
      dataIndex: "categoryPath",
      minWidth: 168,
      ellipsis: true,
    },
    {
      title: "说明",
      key: "note",
      width: 104,
      render: (_, row) =>
        taken.has(row.enCode) ? (
          <span className="text-xs text-[var(--yak-color-text-secondary)]">编码已占用</span>
        ) : (
          "—"
        ),
    },
    {
      title: "操作",
      key: "action",
      width: 84,
      align: "center",
      render: (_, row) => {
        const disabled = taken.has(row.enCode);
        return mode === "single" ? (
          <Button
            size="small"
            variant="ghost"
            disabled={disabled}
            className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
            onClick={() => toggle(row)}
          >
            选择
          </Button>
        ) : (
          <Checkbox
            aria-label={`选择 ${row.cnName}`}
            checked={selectedIds.has(row.id)}
            disabled={disabled}
            onCheckedChange={() => toggle(row)}
          />
        );
      },
    },
  ];
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="w-[880px] rounded-xl p-5 shadow-none">
        <DialogTitle className="text-sm font-medium">
          {mode === "single" ? "绑定数据标准" : "从数据标准导入字段"}
        </DialogTitle>
        <p className="mt-2 text-xs leading-5 text-[var(--yak-color-text-secondary)]">
          {mode === "single"
            ? "绑定后该字段的中文名、英文编码、数据类型与长度/精度会按标准覆盖；非空以标准为默认值，可再修改。"
            : "导入的字段沿用标准的中文名、英文编码、数据类型与长度/精度。"}
        </p>
        <form
          className="mt-3 flex items-center gap-2"
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
            aria-label="搜索数据标准"
            placeholder="中文名称或英文编码"
            onChange={(event) => setSearchText(event.target.value)}
          />
          <Button size="small" type="submit">
            搜索
          </Button>
          {keyword && (
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
        {failed && (
          <div className="mt-3">
            <Alert>
              数据标准加载失败
              <Button size="small" onClick={() => setVersion((value) => value + 1)}>
                重试
              </Button>
            </Alert>
          </div>
        )}
        <div className="mt-3">
          <Table<StandardField>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={loading}
            size="small"
            bordered={false}
            scroll={{ y: "46vh" }}
            emptyText="暂无数据标准"
            pagination={{
              current: pageNo,
              pageSize: PAGE_SIZE,
              total: page?.pagination.total ?? 0,
              disabled: loading,
              onChange: (next) => setPageNo(next),
            }}
          />
        </div>
        {pending && (
          <Alert className="mt-3">
            <div>
              绑定后英文编码将由「{pending.warning.currentCode}」改为「
              {pending.warning.nextCode}」，与来源表 {pending.warning.sourceTable} 的物理列「
              {pending.warning.sourceColumn}」不再一致；下游字段映射需同步调整。
              <div className="mt-2 flex gap-2">
                <Button size="small" onClick={() => setPending(null)}>
                  返回选择
                </Button>
                <Button size="small" variant="primary" onClick={() => onPick([pending.standard])}>
                  确认绑定
                </Button>
              </div>
            </div>
          </Alert>
        )}
        <div className="mt-4 flex items-center justify-between">
          <span className="text-xs text-[var(--yak-color-text-secondary)]">
            {mode === "multiple" ? `已选 ${selected.length} 项` : "点击「选择」绑定到当前字段"}
            {blockedCount > 0 && ` · ${blockedCount} 项因编码已占用不可选`}
          </span>
          <div className="flex gap-2">
            <Button size="small" onClick={onClose}>
              取消
            </Button>
            {mode === "multiple" && (
              <Button
                size="small"
                variant="primary"
                disabled={selected.length === 0}
                onClick={() => onPick(selected)}
              >
                导入 {selected.length} 个字段
              </Button>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
