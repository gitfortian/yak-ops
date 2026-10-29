import { Alert, Button, Dialog, DialogContent, DialogTitle, Textarea } from "@yak-ops/yak-ui";
import { useMemo, useState } from "react";
import type { FieldDraft } from "./fields";
import { parseFields } from "./fields";

interface Props {
  existing: FieldDraft[];
  onClose: () => void;
  onImport: (rows: FieldDraft[]) => void;
}

const SAMPLE_LINES =
  "order_id,订单号,BIGINT\norder_amount,订单金额,DECIMAL,20,2\ncreate_date,创建日期,DATE";
const SAMPLE_DDL = [
  "CREATE TABLE `dwd_trade_order` (",
  "  `order_id` BIGINT NOT NULL COMMENT '订单号',",
  "  `order_amount` DECIMAL(20,2) COMMENT '订单金额',",
  "  PRIMARY KEY (`order_id`)",
  ") ENGINE=OLAP;",
].join("\n");

export default function BatchImportDialog({ existing, onClose, onImport }: Props) {
  const [text, setText] = useState("");
  const parsed = useMemo(() => parseFields(text, existing), [text, existing]);
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="w-[760px] rounded-xl p-5 shadow-none">
        <DialogTitle className="text-sm font-medium">批量录入字段</DialogTitle>
        <p className="mt-2 text-xs leading-5 text-[var(--yak-color-text-secondary)]">
          粘贴 Doris / Paimon 的
          <code className="mx-1 rounded bg-[var(--yak-components-badge-neutral-bg)] px-1">
            CREATE TABLE
          </code>
          语句，自动提取字段、类型、COMMENT 与主键；ENGINE、分区、索引等表级子句会被忽略。
        </p>
        <p className="mt-1 text-xs leading-5 text-[var(--yak-color-text-secondary)]">
          也可以每行一个字段，列之间用 Tab 或逗号分隔：
          <code className="mx-1 rounded bg-[var(--yak-components-badge-neutral-bg)] px-1">
            英文编码, 中文名, 数据类型[, 长度/精度]
          </code>
          。数据类型支持 STRING / BIGINT / DECIMAL / DATE / TIMESTAMP，也可写「字符串 / 整数 / 小数
          / 日期 / 时间戳」。
        </p>
        <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
          行格式示例：{SAMPLE_LINES.replace(/\n/g, "　")}
        </p>
        <div className="mt-3">
          <Textarea
            size="small"
            variant="outlined"
            value={text}
            rows={10}
            spellCheck={false}
            placeholder={SAMPLE_DDL}
            className="font-mono"
            onValueChange={(value) => setText(String(value ?? ""))}
          />
        </div>
        <div className="mt-2 flex items-center gap-3 text-xs">
          <span className="text-[var(--yak-components-field-label)]">
            {parsed.mode === "DDL" ? "CREATE TABLE 解析" : "逐行录入"} · 可导入 {parsed.rows.length}{" "}
            行
          </span>
          {parsed.errors.length > 0 && (
            <span className="text-[var(--yak-components-badge-danger-text)]">
              有 {parsed.errors.length} 行无法解析
            </span>
          )}
        </div>
        {parsed.errors.length > 0 && (
          <div className="mt-2 max-h-32 overflow-auto">
            <Alert>
              <ul className="space-y-1">
                {parsed.errors.map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
            </Alert>
          </div>
        )}
        <div className="mt-4 flex justify-end gap-2">
          <Button size="small" onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            disabled={parsed.rows.length === 0}
            onClick={() => onImport(parsed.rows)}
          >
            {parsed.errors.length > 0
              ? `仅导入可解析的 ${parsed.rows.length} 行`
              : `导入 ${parsed.rows.length} 行`}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
