import { Alert, Button, Dialog, DialogContent, DialogTitle, toast } from "@yak-ops/yak-ui";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { saveCategory, classifyStandardFailure } from "@/service/data-modeling/data-standards";
import type {
  CategoryDraft,
  NodeType,
  SavedStandard,
  StandardKind,
} from "@/service/data-modeling/data-standards/types";
import {
  NODE_TYPE_LABEL,
  NODE_TYPES,
  STANDARD_KIND_LABEL,
  STANDARD_KINDS,
} from "@/service/data-modeling/data-standards/types";
import { InputField, SelectField } from "../shared/form-fields";
export default function CategoryDialog(props: {
  draft: CategoryDraft;
  onClose: () => void;
  onSaved: (node: SavedStandard) => void;
}) {
  const renaming = props.draft.mode === "rename";
  const existing = props.draft.mode === "rename" ? props.draft.category : null;
  const [name, setName] = useState(existing?.name ?? "");
  const [nodeType, setNodeType] = useState<NodeType>(existing?.nodeType ?? "DIRECTORY");
  const [kind, setKind] = useState<StandardKind>(existing?.standardKind ?? "FIELD");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const [stale, setStale] = useState(false);
  const busy = useRef(false),
    active = useRef(true);
  const id = useId();
  // A set that already holds a record keeps its kind; the server would reject the change.
  const kindLocked = Boolean(existing && existing.recordCount > 0);
  const isSet = nodeType === "STANDARD_SET";
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy.current || conflict || stale) return;
    setFailure("");
    if (!name.trim() || name.trim().length > 128) {
      setError("请输入1至128个字符的节点名称");
      return;
    }
    busy.current = true;
    setSaving(true);
    try {
      const saved = await saveCategory(props.draft, {
        name: name.trim(),
        nodeType,
        standardKind: isSet ? kind : null,
      });
      if (active.current) {
        toast.success("保存成功");
        props.onSaved(saved);
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyStandardFailure(error);
      if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "kindLocked") {
        setStale(true);
        setFailure("该标准集已有记录，不能更改标准种类。");
      } else if (result.kind === "missing") {
        setStale(true);
        setFailure("节点已不存在，请关闭弹窗并刷新后重试。");
      } else if (result.kind === "forbidden") {
        setStale(true);
        setFailure("无权修改该节点。");
      } else setFailure(result.kind === "unknown" ? result.message : "节点保存失败，请稍后重试");
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
    }
  };
  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !busy.current) props.onClose();
      }}
    >
      <DialogContent className="w-[520px] rounded-xl p-5 shadow-none">
        <DialogTitle className="mb-5 text-sm font-medium">
          {renaming ? "编辑节点" : "新建目录"}
        </DialogTitle>
        <form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <InputField
            id={id + "-parent"}
            label="上级目录"
            value={props.draft.parentPath || "根目录"}
            disabled
          />
          <InputField
            id={id + "-name"}
            label="节点名称"
            value={name}
            required
            maxLength={128}
            disabled={saving}
            error={error}
            onChange={(value) => {
              setName(value);
              setError("");
            }}
          />
          <SelectField
            id={id + "-node-type"}
            label="节点类型"
            value={nodeType}
            disabled={saving || renaming}
            hint={renaming ? "创建后不能把目录改成标准集，反之亦然。" : undefined}
            items={NODE_TYPES.map((value) => ({ value, label: NODE_TYPE_LABEL[value] }))}
            onChange={(value) => {
              const next = NODE_TYPES.find((item) => item === value);
              if (next) setNodeType(next);
            }}
          />
          {isSet && (
            <SelectField
              id={id + "-kind"}
              label="标准种类"
              value={kind}
              disabled={saving || kindLocked}
              hint={kindLocked ? "该标准集已有记录，种类已锁定；清空记录后才能切换。" : undefined}
              items={STANDARD_KINDS.map((value) => ({ value, label: STANDARD_KIND_LABEL[value] }))}
              onChange={(value) => {
                const next = STANDARD_KINDS.find((item) => item === value);
                if (next) setKind(next);
              }}
            />
          )}
          {conflict && <Alert>数据已变更，请关闭弹窗并刷新后重试。</Alert>}
          {failure && <Alert>{failure}</Alert>}
          <div className="flex justify-end gap-2 pt-2">
            <Button size="small" disabled={saving} onClick={props.onClose}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              type="submit"
              loading={saving}
              disabled={conflict || stale}
            >
              保存
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
