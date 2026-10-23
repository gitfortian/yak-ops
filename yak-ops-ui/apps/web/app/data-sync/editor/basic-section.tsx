import { CollapseSection, Field, FieldLabel, Input, Switch, Textarea } from "@yak-ops/yak-ui";
import { solutionSummary } from "../solution-presentation";
import type { DataSyncDraft } from "@/service/data-sync";
import type { EditorForm } from "./types";

interface Props {
  draft: DataSyncDraft;
  form: EditorForm;
  patch: <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => void;
}
export function BasicSection({ draft, form, patch }: Props) {
  return (
    <CollapseSection id="basic" title="基本信息">
      <div className="space-y-3 rounded-lg border border-[#e6e8eb] bg-white p-4">
        <div className="grid grid-cols-[112px_minmax(0,1fr)] items-center gap-3 text-sm">
          <span>同步方案</span>
          <span>{solutionSummary(draft.solutionType, draft.steps)}</span>
        </div>
        <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>任务名称</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            maxLength={128}
            value={form.name}
            placeholder="请输入任务名称"
            onChange={(event) => patch("name", event.target.value)}
          />
        </Field>
        <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel className="pt-1.5">备注</FieldLabel>
          <Textarea
            size="small"
            rows={2}
            maxLength={500}
            value={form.remark}
            className="min-h-[56px] resize-none"
            placeholder="可选"
            onValueChange={(value) => patch("remark", value)}
          />
        </Field>
        <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>Debug</FieldLabel>
          <Switch
            size="small"
            checked={form.debug}
            onCheckedChange={(checked) => patch("debug", checked)}
          />
        </Field>
      </div>
    </CollapseSection>
  );
}
