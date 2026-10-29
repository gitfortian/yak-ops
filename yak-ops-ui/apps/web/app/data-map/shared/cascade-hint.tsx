import { Button } from "@yak-ops/yak-ui";

/** Reports why a cascading select has no options yet, with an optional manual-input escape hatch. */
export default function CascadeHint({
  loading,
  failed,
  empty,
  emptyText,
  onRetry,
  onFallback,
}: {
  loading: boolean;
  failed: boolean;
  empty: boolean;
  emptyText: string;
  onRetry: () => void;
  onFallback?: () => void;
}) {
  const className = "mt-1 flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]";
  if (failed) {
    return (
      <div className={className}>
        列表加载失败
        <Button size="small" variant="ghost" onClick={onRetry}>
          重试
        </Button>
        {onFallback && (
          <Button size="small" variant="ghost" onClick={onFallback}>
            手动输入
          </Button>
        )}
      </div>
    );
  }
  if (loading) return <div className={className}>正在加载…</div>;
  if (empty) return <div className={className}>{emptyText}</div>;
  return null;
}
