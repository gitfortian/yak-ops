import { Alert, Button, Modal } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import {
  previewDataSyncSchema,
  type DataSyncSchemaPreview,
  type DataSyncTaskSavePayload,
} from "@/service/data-sync";

export function SchemaPreview({
  value,
  disabled,
}: {
  value: DataSyncTaskSavePayload;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<{
    key: string;
    rows?: DataSyncSchemaPreview[];
    error?: string;
  }>({ key: "" });
  const key = JSON.stringify(value);
  useEffect(() => {
    if (!open || disabled) return;
    const controller = new AbortController();
    setResult({ key });
    void previewDataSyncSchema(JSON.parse(key) as DataSyncTaskSavePayload, controller.signal)
      .then((rows) => {
        if (!controller.signal.aborted) setResult({ key, rows });
      })
      .catch((failure) => {
        const reason = failure instanceof Error ? failure.message : "结构预览失败";
        if (!controller.signal.aborted) setResult({ key, error: reason });
      });
    return () => controller.abort();
  }, [open, disabled, key]);
  const current = result.key === key ? result : undefined;
  return (
    <>
      <Button size="small" disabled={disabled} onClick={() => setOpen(true)}>
        DDL 预览
      </Button>
      {open ? (
        <Modal
          open
          centered
          width={760}
          title="DDL 预览"
          onClose={() => setOpen(false)}
          footer={
            <Button size="small" onClick={() => setOpen(false)}>
              关闭
            </Button>
          }
        >
          <div className="max-h-[60vh] space-y-3 overflow-auto">
            <Alert>预览不执行 DDL。暂不支持的字段属性会在预览时提示。</Alert>
            {current?.error ? (
              <Alert>{current.error}</Alert>
            ) : !current?.rows ? (
              <span role="status">加载中…</span>
            ) : (
              current.rows.map((row) => (
                <section key={row.mappingIndex} className="space-y-2">
                  <div className="text-sm font-medium">
                    {[row.target.catalog, row.target.schema, row.target.table]
                      .filter(Boolean)
                      .join(".")}{" "}
                    · {row.action === "CREATE" ? "创建" : row.action === "ALTER" ? "调整结构" : "结构兼容"}
                  </div>
                  {row.differences.filter((difference) => difference.action !== "COMPATIBLE").length > 0 ? (
                    <div className="overflow-auto rounded-lg border text-xs">
                      {row.differences
                        .filter((difference) => difference.action !== "COMPATIBLE")
                        .map((difference, index) => (
                          <div
                            key={`${difference.column ?? "table"}-${difference.reason}-${index}`}
                            className="flex flex-wrap gap-x-3 gap-y-1 border-b px-3 py-2 last:border-b-0"
                          >
                            <span className="min-w-28 font-medium">{difference.column ?? "目标表"}</span>
                            <span>
                              {difference.action === "CREATE_TABLE"
                                ? "创建表"
                                : difference.action === "ADD_COLUMN"
                                  ? "新增字段"
                                  : difference.action === "ALTER_COLUMN_TYPE"
                                    ? "调整字段类型"
                                    : difference.action === "METADATA_DIFFERENCE"
                                      ? "物理属性存在差异（不自动迁移）"
                                      : "结构不兼容"}
                            </span>
                            {difference.sourceType || difference.targetType ? (
                              <span className="text-[var(--color-text-secondary)]">
                                {[difference.targetType, difference.sourceType].filter(Boolean).join(" → ")}
                              </span>
                            ) : null}
                          </div>
                        ))}
                    </div>
                  ) : null}
                  {row.statements.length > 0 ? (
                    <pre className="overflow-auto rounded-lg border p-3 text-xs">
                      <code>{row.statements.join(";\n\n")};</code>
                    </pre>
                  ) : null}
                </section>
              ))
            )}
          </div>
        </Modal>
      ) : null}
    </>
  );
}
