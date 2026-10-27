import {
  Alert,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
  toast,
} from "@yak-ops/yak-ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { dataMapFailureMessage } from "@/service/data-map/request";
import { assignTags, createTag, deleteTag, renameTag } from "@/service/data-map/tags";

import { dataMapKeys } from "../../shared/keys";
import { useTagOptions } from "../../shared/lookups";
import { useDataMapScope } from "../../shared/scope";

interface Props {
  tableId: string;
  selectedTagIds: string[];
  onClose: () => void;
  onSaved: () => void;
}

/** Editing session for the Workspace tag vocabulary and this asset's tags. */
export default function TagDialog({ tableId, selectedTagIds, onClose, onSaved }: Props) {
  const { scope } = useDataMapScope();
  const queryClient = useQueryClient();
  const tags = useTagOptions();
  const [selected, setSelected] = useState<string[]>(selectedTagIds);
  const [draftName, setDraftName] = useState("");
  const [editing, setEditing] = useState<{ id: string; name: string } | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState("");
  const [failure, setFailure] = useState("");

  const refresh = () => void queryClient.invalidateQueries({ queryKey: dataMapKeys.tags(scope) });
  const onError = (error: unknown) => setFailure(dataMapFailureMessage(error, "操作失败"));

  const create = useMutation({
    mutationFn: () => createTag(draftName.trim()),
    onSuccess: (tag) => {
      setDraftName("");
      setSelected((current) => [...current, tag.id]);
      refresh();
    },
    onError,
  });

  const rename = useMutation({
    mutationFn: (input: { id: string; name: string }) => renameTag(input.id, input.name),
    onSuccess: () => {
      setEditing(null);
      refresh();
    },
    onError,
  });

  const remove = useMutation({
    mutationFn: (id: string) => deleteTag(id),
    onSuccess: (_result, id) => {
      setConfirmingDelete("");
      setSelected((current) => current.filter((tagId) => tagId !== id));
      refresh();
    },
    onError,
  });

  const save = useMutation({
    mutationFn: () => assignTags(tableId, selected),
    onSuccess: () => {
      refresh();
      toast.success("标签已保存");
      onSaved();
    },
    onError,
  });

  const toggle = (id: string, checked: boolean) =>
    setSelected((current) =>
      checked ? [...new Set([...current, id])] : current.filter((tagId) => tagId !== id),
    );

  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !save.isPending) onClose();
      }}
    >
      <DialogContent className="w-[480px]">
        <DialogTitle className="text-sm font-medium">标签</DialogTitle>
        <DialogDescription className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
          标签属于当前工作空间，可被多个资产共用。
        </DialogDescription>

        <div className="mt-4 flex items-center gap-2">
          <Input
            size="small"
            variant="outlined"
            value={draftName}
            placeholder="新建标签名称"
            aria-label="新建标签名称"
            onChange={(event) => setDraftName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && draftName.trim()) create.mutate();
            }}
          />
          <Button
            size="small"
            loading={create.isPending}
            disabled={!draftName.trim()}
            onClick={() => create.mutate()}
          >
            新建
          </Button>
        </div>

        <div className="mt-3 max-h-64 space-y-1 overflow-auto">
          {(tags.data ?? []).length === 0 && !tags.isPending && (
            <p className="text-xs text-[var(--yak-components-muted-text)]">还没有标签</p>
          )}
          {(tags.data ?? []).map((tag) => (
            <div key={tag.id} className="flex items-center gap-2 py-1">
              <Checkbox
                checked={selected.includes(tag.id)}
                aria-label={tag.name}
                onCheckedChange={(checked) => toggle(tag.id, checked === true)}
              />
              {editing?.id === tag.id ? (
                <>
                  <Input
                    size="small"
                    variant="outlined"
                    value={editing.name}
                    aria-label="标签名称"
                    onChange={(event) => setEditing({ id: tag.id, name: event.target.value })}
                  />
                  <Button
                    size="small"
                    loading={rename.isPending}
                    disabled={!editing.name.trim()}
                    onClick={() => rename.mutate({ id: tag.id, name: editing.name.trim() })}
                  >
                    保存
                  </Button>
                  <Button size="small" variant="ghost" onClick={() => setEditing(null)}>
                    取消
                  </Button>
                </>
              ) : (
                <>
                  <span className="min-w-0 flex-1 truncate text-[13px]">{tag.name}</span>
                  {confirmingDelete === tag.id ? (
                    <>
                      <Button
                        size="small"
                        variant="danger"
                        loading={remove.isPending}
                        onClick={() => remove.mutate(tag.id)}
                      >
                        确认删除
                      </Button>
                      <Button size="small" variant="ghost" onClick={() => setConfirmingDelete("")}>
                        取消
                      </Button>
                    </>
                  ) : (
                    <>
                      <Button
                        size="small"
                        variant="ghost"
                        onClick={() => setEditing({ id: tag.id, name: tag.name })}
                      >
                        重命名
                      </Button>
                      <Button
                        size="small"
                        variant="ghost"
                        onClick={() => setConfirmingDelete(tag.id)}
                      >
                        删除
                      </Button>
                    </>
                  )}
                </>
              )}
            </div>
          ))}
        </div>

        {failure && <Alert className="mt-3">{failure}</Alert>}

        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={save.isPending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            loading={save.isPending}
            onClick={() => save.mutate()}
          >
            保存标签
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
