import { useCallback, useState } from "react";
import { exportSemanticModel, type SemanticExport } from "@/service/data-modeling/semantic";
import { Button } from "@yak-ops/yak-ui";

/**
 * 语义导出页签：发布体检（❌/⚠️/✅）先行，确认后才下载 YAML。
 * 检查与文档来自同一次导出请求，永不漂移；未发布模型展示设计态名称横幅。
 */
export function SemanticExportPanel({
  modelId,
  published,
}: {
  modelId: string;
  published: boolean;
}) {
  const [result, setResult] = useState<SemanticExport | null>(null);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [copied, setCopied] = useState(false);
  const [confirmed, setConfirmed] = useState(false);

  const run = useCallback(() => {
    setLoading(true);
    setFailed(false);
    setConfirmed(false);
    exportSemanticModel(modelId)
      .then(setResult)
      .catch(() => setFailed(true))
      .finally(() => setLoading(false));
  }, [modelId]);

  const blocking = (result?.checks ?? []).filter((check) => check.level === "ERROR");
  const warnings = (result?.checks ?? []).filter((check) => check.level === "WARN");
  const okays = (result?.checks ?? []).filter((check) => check.level === "OK");

  const download = () => {
    if (!result) return;
    const blob = new Blob([result.yaml], { type: "application/x-yaml" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${modelId}.ossie.yaml`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const copy = async () => {
    if (!result) return;
    await navigator.clipboard.writeText(result.yaml);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-2.5 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-badge-warning-bg)] p-3 text-xs leading-5 text-[var(--yak-components-badge-warning-text)]">
        <div>
          <b>规范版本 0.2.0.dev0（草案）</b>
          <br />
          导出为单向投影，不写回、不作存储格式；规范升级可能改变导出内容。
          {!published && (
            <>
              <br />
              <b>该模型未发布，导出的是设计态名称。</b>
            </>
          )}
        </div>
      </div>

      <div className="rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)]">
        <div className="flex items-center justify-between border-b border-[var(--yak-components-panel-border)] px-4 py-2.5">
          <h3 className="text-sm font-medium">发布体检</h3>
          <Button size="small" onClick={run} disabled={loading}>
            {loading ? "检查中…" : "重新体检并生成"}
          </Button>
        </div>
        <div className="space-y-2 p-3">
          {failed && (
            <p className="text-xs text-[var(--yak-components-badge-danger-text)]">
              体检失败，请稍后重试。
            </p>
          )}
          {!result && !loading && !failed && (
            <p className="text-xs leading-5 text-[var(--yak-components-field-label)]">
              点击「重新体检并生成」生成导出前检查与 Ossie 文档。
            </p>
          )}
          {result && checksView(blocking, warnings, okays, () => setConfirmed(true))}
        </div>
      </div>

      {result && confirmed && (
        <div className="rounded-xl border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)]">
          <div className="flex items-center justify-between border-b border-[var(--yak-components-panel-border)] px-4 py-2.5">
            <h3 className="text-sm font-medium">{modelId}.ossie.yaml</h3>
            <div className="flex gap-2">
              <Button size="small" onClick={copy}>
                {copied ? "已复制" : "复制"}
              </Button>
              <Button size="small" variant="primary" onClick={download}>
                下载文件
              </Button>
            </div>
          </div>
          <pre className="max-h-[420px] overflow-auto rounded-b-xl bg-[var(--yak-color-surface-muted)] p-3.5 font-mono text-xs leading-6 text-[var(--yak-color-text-primary)]">
            {result.yaml}
          </pre>
        </div>
      )}
    </div>
  );
}

function checksView(
  blocking: SemanticExport["checks"],
  warnings: SemanticExport["checks"],
  okays: SemanticExport["checks"],
  confirm: () => void,
) {
  const hasBlocking = blocking.length > 0;
  return (
    <>
      {blocking.map((check, index) => (
        <p
          key={`e${index}`}
          className="rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-badge-danger-bg)] p-2.5 text-xs text-[var(--yak-components-badge-danger-text)]"
        >
          ❌ {check.message}
        </p>
      ))}
      {warnings.map((check, index) => (
        <p
          key={`w${index}`}
          className="rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-badge-warning-bg)] p-2.5 text-xs text-[var(--yak-components-badge-warning-text)]"
        >
          ⚠️ {check.message}
        </p>
      ))}
      {okays.map((check, index) => (
        <p key={`o${index}`} className="text-xs text-[var(--yak-components-badge-success-text)]">
          ✅ {check.message}
        </p>
      ))}
      <div className="flex items-center justify-end gap-3 pt-1">
        {hasBlocking && (
          <span className="text-xs text-[var(--yak-components-badge-danger-text)]">
            存在已移除的关联键字段，请先修复关系后再导出。
          </span>
        )}
        <Button size="small" variant="primary" disabled={hasBlocking} onClick={confirm}>
          生成导出文档
        </Button>
      </div>
    </>
  );
}
