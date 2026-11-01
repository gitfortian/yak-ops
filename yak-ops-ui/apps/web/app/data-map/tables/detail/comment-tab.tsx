import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { Button, toast } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";

import { useAuth } from "@/hooks/use-auth";

import { dataMapFailureMessage } from "@/service/data-map/request";
import {
  createComment,
  deleteComment,
  markCommentsRead,
  pageComments,
  type CommentVO,
} from "@/service/data-map/comment";

import { dataMapKeys } from "../../shared/keys";
import UserAvatar from "../../shared/user-avatar";

interface Props {
  tableId: string;
}

/** 资产详情的评论 Tab：扁平线程 + 单层回复，@ 记号仅作视觉标记。 */
export default function CommentTab({ tableId }: Props) {
  const queryClient = useQueryClient();
  const { currentUser } = useAuth();
  const [draft, setDraft] = useState("");
  const [replyTo, setReplyTo] = useState<CommentVO | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const comments = useQuery({
    queryKey: dataMapKeys.commentThread(tableId),
    queryFn: ({ signal }) => pageComments(tableId, 1, 50, signal),
  });

  // 打开 Tab 即标记已读，驱动 Tab 标签上的未读徽标归零。
  useEffect(() => {
    if (!comments.isSuccess) return;
    markCommentsRead(tableId)
      .then(() => {
        void queryClient.invalidateQueries({ queryKey: dataMapKeys.commentUnread(tableId) });
      })
      .catch(() => {});
  }, [comments.isSuccess, tableId, queryClient]);

  const create = useMutation({
    mutationFn: (body: string) => createComment(tableId, body, replyTo?.id ?? undefined),
    onSuccess: () => {
      setDraft("");
      setReplyTo(null);
      void queryClient.invalidateQueries({ queryKey: dataMapKeys.commentThread(tableId) });
    },
    onError: (error) => toast.error(dataMapFailureMessage(error, "评论失败，请稍后重试")),
  });

  const remove = useMutation({
    mutationFn: deleteComment,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: dataMapKeys.commentThread(tableId) });
      void queryClient.invalidateQueries({ queryKey: dataMapKeys.commentUnread(tableId) });
      toast.success("已删除");
    },
    onError: (error) => toast.error(dataMapFailureMessage(error, "删除失败，请稍后重试")),
  });

  const records = comments.data?.bizData ?? [];

  const submit = () => {
    const body = draft.trim();
    if (!body || create.isPending) return;
    create.mutate(body);
  };

  const toggleReplies = (commentId: string) => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(commentId)) next.delete(commentId);
      else next.add(commentId);
      return next;
    });
  };

  const canDelete = (comment: CommentVO) => comment.authorId === currentUser?.id;

  const replyToThread = (comment: CommentVO) => {
    setReplyTo(comment);
    setDraft(`@${comment.authorName} `);
  };

  return (
    <div className="flex min-h-0 flex-col gap-4">
      <h3 className="text-[13px] font-bold">评论</h3>

      <div className="flex items-start gap-2.5">
        <UserAvatar name={currentUser?.realName ?? currentUser?.userName ?? "?"} size="md" />
        <div className="min-w-0 flex-1">
          <textarea
            value={draft}
            rows={2}
            maxLength={2000}
            placeholder={replyTo ? `回复 ${replyTo.authorName}…` : "添加评论，@成员名 可标记对方"}
            className="w-full resize-none rounded-[var(--yak-radius-md)] border border-[var(--yak-components-input-border)] bg-[var(--yak-components-input-bg)] px-3 py-2 text-sm outline-none transition-colors placeholder:text-[var(--yak-components-input-placeholder)] focus:border-[var(--yak-components-input-border-focus)]"
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) submit();
            }}
          />
          <div className="mt-1.5 flex items-center justify-between">
            <span className="text-[11px] text-[var(--yak-components-muted-text)]">
              Ctrl+Enter 提交
              {replyTo && (
                <>
                  · 回复 {replyTo.authorName}
                  <button
                    type="button"
                    className="ml-1 cursor-pointer hover:underline"
                    onClick={() => setReplyTo(null)}
                  >
                    取消
                  </button>
                </>
              )}
            </span>
            <Button
              size="small"
              variant="primary"
              disabled={!draft.trim() || create.isPending}
              onClick={submit}
            >
              {replyTo ? "回复" : "评论"}
            </Button>
          </div>
        </div>
      </div>

      {comments.isPending ? (
        <p className="py-6 text-center text-xs text-[var(--yak-components-muted-text)]">加载中</p>
      ) : records.length === 0 ? (
        <p className="py-6 text-center text-xs text-[var(--yak-components-muted-text)]">
          还没有评论，第一条评论会出现在这里。
        </p>
      ) : (
        <ol className="m-0 list-none space-y-1 p-0">
          {records.map((comment) => (
            <li key={comment.id} className="group">
              <CommentRow
                comment={comment}
                onDelete={canDelete(comment) ? () => remove.mutate(comment.id) : undefined}
              />
              {comment.replyCount > 0 && (
                <button
                  type="button"
                  className="ml-9 cursor-pointer text-xs text-[var(--yak-color-primary)]"
                  onClick={() => toggleReplies(comment.id)}
                >
                  {expanded.has(comment.id) ? "收起回复" : `${comment.replyCount} 条回复`}
                </button>
              )}
              {expanded.has(comment.id) && (
                <div className="ml-9 space-y-1 border-l border-[var(--yak-components-panel-border)] pl-3">
                  {comment.replies.map((reply) => (
                    <CommentRow
                      key={reply.id}
                      comment={reply}
                      onDelete={canDelete(reply) ? () => remove.mutate(reply.id) : undefined}
                      compact
                    />
                  ))}
                  <button
                    type="button"
                    className="ml-1 cursor-pointer text-xs text-[var(--yak-color-primary)]"
                    onClick={() => replyToThread(comment)}
                  >
                    回复
                  </button>
                </div>
              )}
              {comment.replyCount === 0 && (
                <button
                  type="button"
                  className="ml-9 cursor-pointer text-xs text-[var(--yak-components-muted-text)] opacity-0 transition-opacity group-hover:opacity-100 hover:text-[var(--yak-color-primary)]"
                  onClick={() => replyToThread(comment)}
                >
                  回复
                </button>
              )}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function CommentRow({
  comment,
  onDelete,
  compact = false,
}: {
  comment: CommentVO;
  onDelete?: () => void;
  compact?: boolean;
}) {
  return (
    <div className={`flex items-start gap-2.5 py-2 ${compact ? "ml-0" : ""}`}>
      <UserAvatar name={comment.authorName} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-[var(--yak-components-panel-text)]">
            {comment.authorName}
          </span>
          <span className="text-[11px] text-[var(--yak-components-muted-text)]">
            {formatCommentTime(comment.createTime)}
          </span>
          {onDelete && (
            <button
              type="button"
              aria-label="删除评论"
              className="ml-auto cursor-pointer text-[var(--yak-components-muted-text)] opacity-0 transition-opacity hover:text-[var(--yak-components-badge-danger-text)] group-hover:opacity-100"
              onClick={onDelete}
            >
              <Trash2 size={12} aria-hidden="true" />
            </button>
          )}
        </div>
        <p className="mt-0.5 text-[13px] leading-5 text-[var(--yak-components-panel-text)]">
          {renderBody(comment.body)}
        </p>
      </div>
    </div>
  );
}

/**
 * Renders @tokens that start a word as accent chips; plain text stays plain. The word-boundary
 * lookbehind keeps email addresses like user@example.com out of the chip styling.
 */
function renderBody(body: string) {
  const parts = body.split(/((?<=^|\s)@[^\s@]+)/g);
  return parts.map((part, index) =>
    part.startsWith("@") ? (
      <span
        key={index}
        className="rounded bg-[var(--yak-color-primary-subtle)] px-1 font-medium text-[var(--yak-color-primary)]"
      >
        {part}
      </span>
    ) : (
      part
    ),
  );
}

function formatCommentTime(value: string) {
  const time = new Date(value);
  if (Number.isNaN(time.getTime())) return value;
  const now = new Date();
  const diffMs = now.getTime() - time.getTime();
  if (diffMs < 60000) return "刚刚";
  if (diffMs < 3600000) return `${Math.floor(diffMs / 60000)} 分钟前`;
  if (diffMs < 86400000) return `${Math.floor(diffMs / 3600000)} 小时前`;
  return `${time.getMonth() + 1}-${time.getDate()} ${String(time.getHours()).padStart(2, "0")}:${String(time.getMinutes()).padStart(2, "0")}`;
}
