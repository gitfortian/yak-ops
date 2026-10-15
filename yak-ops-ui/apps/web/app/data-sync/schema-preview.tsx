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
      .catch(() => {
        if (!controller.signal.aborted)
          setResult({ key, error: "结构预览失败，请检查字段类型、目标结构与权限" });
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
            <Alert>仅迁移字段、主键和注释；默认值、自增列、生成列不支持。预览不执行 DDL。</Alert>
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
                    · {row.action === "CREATE" ? "创建并校验" : "已有结构校验通过"}
                  </div>
                  {row.action === "CREATE" ? (
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
