import {
  Button,
  SectionCard,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
  toast,
} from "@yak-ops/yak-ui";
import { Copy, Download, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { downloadTaskInstanceLogs, getTaskInstanceLogs } from "@/service/task-execution";

interface Props {
  instanceId: string;
  active: boolean;
  embedded?: boolean;
}

export function TaskInstanceLogPanel({ instanceId, active, embedded = false }: Props) {
  const [content, setContent] = useState("");
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const bodyRef = useRef<HTMLPreElement>(null);

  useEffect(() => {
    let disposed = false;
    let position: string | undefined;
    let loading = false;
    let more = true;

    const load = async () => {
      if (loading || disposed) return;
      loading = true;
      try {
        let pages = 0;
        let hasMore: boolean;
        do {
          const page = await getTaskInstanceLogs(instanceId, position);
          if (disposed) return;
          position = page.nextCursor;
          if (page.content) {
            setContent((previous) => (previous + page.content).slice(-180000));
          }
          hasMore = page.hasMore;
          pages += 1;
        } while (hasMore && pages < 5);
        more = hasMore;
        setError(false);
      } catch {
        if (!disposed) setError(true);
      } finally {
        loading = false;
      }
    };

    setContent("");
    setError(false);
    void load();
    const refresh = window.setInterval(() => {
      if (active || more) void load();
    }, 2000);
    return () => {
      disposed = true;
      window.clearInterval(refresh);
    };
  }, [instanceId, active, revision]);

  useEffect(() => {
    const element = bodyRef.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [content]);

  const copy = async () => {
    if (!content) return;
    try {
      await navigator.clipboard.writeText(content);
      toast.success("已复制日志");
    } catch {
      toast.error("复制失败");
    }
  };

  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const file = await downloadTaskInstanceLogs(instanceId);
      const url = URL.createObjectURL(file);
      const link = document.createElement("a");
      link.href = url;
      link.download = `task-${instanceId}.log`;
      link.click();
      URL.revokeObjectURL(url);
    } finally {
      setDownloading(false);
    }
  };

  const contentView = (
    <TooltipProvider>
      <div className="relative rounded-xl bg-[#151b2d]">
        <div className="absolute top-3 right-3 z-10 flex gap-1">
          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  size="small"
                  variant="ghost"
                  className="h-8 w-8 rounded-md border-0 bg-white/10 p-0 text-[#ecf0f8] hover:bg-white/20"
                  aria-label="刷新日志"
                  onClick={() => setRevision((value) => value + 1)}
                >
                  <RefreshCw size={14} aria-hidden="true" />
                </Button>
              }
            />
            <TooltipContent side="top">刷新日志</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  size="small"
                  variant="ghost"
                  className="h-8 w-8 rounded-md border-0 bg-white/10 p-0 text-[#ecf0f8] hover:bg-white/20"
                  aria-label="复制日志"
                  disabled={!content}
                  onClick={() => void copy()}
                >
                  <Copy size={14} aria-hidden="true" />
                </Button>
              }
            />
            <TooltipContent side="top">复制日志</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  size="small"
                  variant="ghost"
                  className="h-8 w-8 rounded-md border-0 bg-white/10 p-0 text-[#ecf0f8] hover:bg-white/20"
                  aria-label="下载日志"
                  loading={downloading}
                  onClick={() => void download()}
                >
                  <Download size={14} aria-hidden="true" />
                </Button>
              }
            />
            <TooltipContent side="top">下载日志</TooltipContent>
          </Tooltip>
        </div>
        <pre
          ref={bodyRef}
          className="max-h-96 min-h-36 overflow-auto rounded-xl p-4 text-xs leading-5 whitespace-pre-wrap break-words text-[#ecf0f8]"
        >
          {content || (error ? "日志加载失败" : "暂无日志")}
        </pre>
      </div>
    </TooltipProvider>
  );

  if (embedded) return contentView;
  return <SectionCard title="执行日志">{contentView}</SectionCard>;
}
