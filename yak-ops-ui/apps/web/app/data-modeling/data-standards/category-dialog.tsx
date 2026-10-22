import { Alert, Button, Dialog, DialogContent, DialogTitle, Form, toast } from "@yak-ops/yak-ui";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { saveCategory, classifyStandardFailure } from "@/service/data-modeling/data-standards";
import type { CategoryDraft, SavedStandard } from "@/service/data-modeling/data-standards/types";
import { InputField } from "./form-fields";
export default function CategoryDialog(props: {
  draft: CategoryDraft;
  onClose: () => void;
  onSaved: (node: SavedStandard) => void;
}) {
  const [name, setName] = useState(props.draft.mode === "rename" ? props.draft.category.name : "");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [failure, setFailure] = useState("");
  const [stale, setStale] = useState(false);
  const busy = useRef(false),
    active = useRef(true);
  const id = useId();
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
      setError("请输入1至128个字符的目录名称");
      return;
    }
    busy.current = true;
    setSaving(true);
    try {
      const saved = await saveCategory(props.draft, name.trim());
      if (active.current) {
        toast.success("保存成功");
        props.onSaved(saved);
      }
    } catch (error) {
      if (!active.current) return;
      const result = classifyStandardFailure(error);
      if (result.kind === "conflict") setConflict(true);
      else if (result.kind === "missing") {
        setStale(true);
        setFailure("目录已不存在，请关闭弹窗并刷新后重试。");
      } else if (result.kind === "forbidden") {
        setStale(true);
        setFailure("无权修改该目录。");
      } else setFailure(result.kind === "unknown" ? result.message : "目录保存失败，请稍后重试");
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
          {props.draft.mode === "rename" ? "重命名目录" : "新建目录"}
        </DialogTitle>
        <Form noValidate onSubmit={(event) => void submit(event)} className="space-y-3">
          <InputField
            id={id + "-parent"}
            label="上级目录"
            value={props.draft.parentPath || "根目录"}
            disabled
          />
          <InputField
            id={id + "-name"}
            label="目录名称"
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
        </Form>
      </DialogContent>
    </Dialog>
  );
}
