import {
  Button,
  Checkbox,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  toast,
} from "@yak-ops/yak-ui";
import { useState } from "react";
import type { StandardField } from "@/service/data-modeling/data-standards/types";
import {
  AGGREGATE_LABEL,
  AGGREGATE_TYPES,
  aggregateSupports,
  type AggregateType,
} from "@/service/data-modeling/dimension-modeling/types";
import BatchImportDialog from "./batch-import-dialog";
import ImportDialog from "./import-dialog";
import type { FieldDraft, FieldIssue, FieldIssueField } from "./fields";
import { emptyField, fromStandard, TYPE_ITEMS } from "./fields";
import StandardPicker from "./standard-picker";

interface Props {
  rows: FieldDraft[];
  onChange: (rows: FieldDraft[]) => void;
  issues: FieldIssue[];
  disabled?: boolean;
  /** True for DWS and ADS: those kinds own the measure column, DWD and DIM never show it. */
  allowAggregate?: boolean;
  /** True for DWS: the server rejects a save without any measure, so the last one is locked. */
  requireMeasure?: boolean;
  /** Reverse modeling already produced the draft, so the import actions would re-import elsewhere. */
  hideImportActions?: boolean;
}

type Picker = { mode: "single"; index: number } | { mode: "multiple" };

const cell = "border-b border-[#eaecf0] px-2 py-1.5 align-middle";

export default function FieldEditor({
  rows,
  onChange,
  issues,
  disabled = false,
  allowAggregate = false,
  requireMeasure = false,
  hideImportActions = false,
}: Props) {
  const [picker, setPicker] = useState<Picker | null>(null);
  const [importing, setImporting] = useState(false);
  const [importingSource, setImportingSource] = useState(false);
  const patch = (index: number, partial: Partial<FieldDraft>) => {
    onChange(rows.map((row, position) => (position === index ? { ...row, ...partial } : row)));
  };
  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= rows.length) return;
    const next = [...rows];
    const [moved] = next.splice(index, 1);
    next.splice(target, 0, moved);
    onChange(next);
  };
  const issueFor = (index: number, field: FieldIssueField) =>
    issues.find((issue) => issue.index === index && issue.field === field)?.message;
  const bindStandard = (index: number, standard: StandardField) => {
    const replacement = fromStandard(standard);
    onChange(
      rows.map((row, position) =>
        position === index
          ? {
              ...row,
              cnName: row.cnName.trim() || replacement.cnName,
              enCode: replacement.enCode,
              dataType: replacement.dataType,
              lengthPrecision: replacement.lengthPrecision,
              isNotNull: replacement.isNotNull,
              standardId: replacement.standardId,
              standardEnCode: replacement.standardEnCode,
              importNote: null,
            }
          : row,
      ),
    );
    setPicker(null);
  };
  const importStandards = (standards: StandardField[]) => {
    const taken = new Set(rows.map((row) => row.enCode.trim()));
    const added = standards.filter((standard) => !taken.has(standard.enCode));
    if (added.length < standards.length)
      toast.info(`已跳过 ${standards.length - added.length} 个编码重复的标准`);
    onChange([...rows, ...added.map(fromStandard)]);
    setPicker(null);
  };
  const partitionCount = rows.filter((row) => row.isPartition).length;
  const keyCount = rows.filter((row) => row.isPrimaryKey).length;
  const measureCount = rows.filter((row) => row.aggregateType != null).length;
  return (
    <div className="flex min-w-0 flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <Button size="small" disabled={disabled} onClick={() => onChange([...rows, emptyField()])}>
          添加字段
        </Button>
        {!hideImportActions && (
          <>
            <Button size="small" disabled={disabled} onClick={() => setImporting(true)}>
              批量录入
            </Button>
            <Button size="small" disabled={disabled} onClick={() => setImportingSource(true)}>
              从表/视图导入
            </Button>
            <Button
              size="small"
              disabled={disabled}
              onClick={() => setPicker({ mode: "multiple" })}
            >
              从数据标准导入
            </Button>
          </>
        )}
        <span className="text-xs text-[#667475]">
          共 {rows.length} 个字段，主键 {keyCount} 个，分区 {partitionCount} 个
          {allowAggregate && `，度量 ${measureCount} 个`}
        </span>
      </div>
      {issues.length > 0 && (
        <ul className="space-y-1 rounded-lg border border-[#fecdca] bg-[#fef3f2] px-3 py-2 text-xs text-[#b42318]">
          {issues.slice(0, 6).map((issue) => (
            <li key={`${issue.index}-${issue.field}-${issue.message}`}>
              第 {issue.index + 1} 行：{issue.message}
            </li>
          ))}
          {issues.length > 6 && <li>另有 {issues.length - 6} 个问题未列出</li>}
        </ul>
      )}
      <div className="min-w-0 overflow-auto rounded-lg border border-[var(--yak-components-panel-border)]">
        <table
          className={`w-full ${allowAggregate ? "min-w-[1530px]" : "min-w-[1420px]"} border-collapse text-xs`}
        >
          <thead className="sticky top-0 z-10 bg-[#fafafa] text-left text-[#475467]">
            <tr>
              <th className="w-[52px] border-b border-[#eaecf0] px-2 py-2 font-medium">序号</th>
              <th className="w-[180px] border-b border-[#eaecf0] px-2 py-2 font-medium">
                中文名<span className="text-[#d92d20]">*</span>
              </th>
              <th className="w-[200px] border-b border-[#eaecf0] px-2 py-2 font-medium">
                英文编码<span className="text-[#d92d20]">*</span>
              </th>
              <th className="w-[128px] border-b border-[#eaecf0] px-2 py-2 font-medium">
                数据类型<span className="text-[#d92d20]">*</span>
              </th>
              <th className="w-[112px] border-b border-[#eaecf0] px-2 py-2 font-medium">
                长度/精度
              </th>
              <th className="w-[56px] border-b border-[#eaecf0] px-2 py-2 text-center font-medium">
                主键
              </th>
              <th className="w-[56px] border-b border-[#eaecf0] px-2 py-2 text-center font-medium">
                非空
              </th>
              <th className="w-[56px] border-b border-[#eaecf0] px-2 py-2 text-center font-medium">
                分区
              </th>
              {allowAggregate && (
                <th className="w-[128px] border-b border-[#eaecf0] px-2 py-2 font-medium">聚合</th>
              )}
              <th className="w-[176px] border-b border-[#eaecf0] px-2 py-2 font-medium">
                数据标准
              </th>
              <th className="w-[240px] border-b border-[#eaecf0] px-2 py-2 font-medium">
                字段描述
              </th>
              <th className="w-[124px] border-b border-[#eaecf0] px-2 py-2 text-center font-medium">
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => {
              const bound = Boolean(row.standardId);
              // A DWS row without a measure is rejected on save, so the last measure cannot be
              // dropped by deleting its row nor by clearing its aggregate.
              const lastMeasure = requireMeasure && row.aggregateType != null && measureCount === 1;
              return (
                <tr key={row.key} className="hover:bg-[#fcfcfd]">
                  <td className={`${cell} text-[#667475]`}>{index + 1}</td>
                  <td className={cell}>
                    <Input
                      size="small"
                      variant="outlined"
                      value={row.cnName}
                      maxLength={128}
                      disabled={disabled}
                      aria-label={`第 ${index + 1} 行中文名`}
                      aria-invalid={Boolean(issueFor(index, "cnName")) || undefined}
                      onChange={(event) => patch(index, { cnName: event.target.value })}
                    />
                  </td>
                  <td className={cell}>
                    <Input
                      size="small"
                      variant="outlined"
                      value={row.enCode}
                      maxLength={64}
                      readOnly={bound}
                      disabled={disabled}
                      placeholder="order_id"
                      aria-label={`第 ${index + 1} 行英文编码`}
                      aria-invalid={Boolean(issueFor(index, "enCode")) || undefined}
                      onChange={(event) => patch(index, { enCode: event.target.value })}
                    />
                  </td>
                  <td className={cell}>
                    <Select
                      size="small"
                      value={row.dataType}
                      items={TYPE_ITEMS}
                      disabled={disabled || bound}
                      onValueChange={(value) => {
                        const next = TYPE_ITEMS.find((item) => item.value === value)?.value ?? null;
                        patch(index, {
                          dataType: next,
                          lengthPrecision:
                            next === "STRING" || next === "DECIMAL" ? row.lengthPrecision : "",
                          importNote: null,
                        });
                      }}
                    >
                      <SelectTrigger variant="outlined" aria-label={`第 ${index + 1} 行数据类型`}>
                        <SelectValue placeholder="类型" />
                      </SelectTrigger>
                      <SelectContent>
                        {TYPE_ITEMS.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            <SelectItemText>{item.label}</SelectItemText>
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {row.importNote && (
                      <p className="mt-1 text-[10px] leading-3 text-[#b42318]">{row.importNote}</p>
                    )}
                  </td>
                  <td className={cell}>
                    <Input
                      size="small"
                      variant="outlined"
                      value={row.lengthPrecision}
                      maxLength={32}
                      readOnly={bound}
                      disabled={
                        disabled || (row.dataType !== "STRING" && row.dataType !== "DECIMAL")
                      }
                      aria-label={`第 ${index + 1} 行长度或精度`}
                      onChange={(event) => patch(index, { lengthPrecision: event.target.value })}
                    />
                  </td>
                  <td className={`${cell} text-center`}>
                    <Checkbox
                      aria-label={`第 ${index + 1} 行主键`}
                      checked={row.isPrimaryKey}
                      disabled={disabled}
                      onCheckedChange={(checked) => patch(index, { isPrimaryKey: checked })}
                    />
                  </td>
                  <td className={`${cell} text-center`}>
                    <Checkbox
                      aria-label={`第 ${index + 1} 行非空`}
                      checked={row.isNotNull}
                      disabled={disabled}
                      onCheckedChange={(checked) => patch(index, { isNotNull: checked })}
                    />
                  </td>
                  <td className={`${cell} text-center`}>
                    <Checkbox
                      aria-label={`第 ${index + 1} 行分区`}
                      checked={row.isPartition}
                      disabled={disabled}
                      onCheckedChange={(checked) => patch(index, { isPartition: checked })}
                    />
                  </td>
                  {allowAggregate && (
                    <td className={cell}>
                      <Select
                        value={row.aggregateType ?? ""}
                        onValueChange={(value) =>
                          patch(index, {
                            aggregateType: (value || null) as AggregateType | null,
                          })
                        }
                      >
                        <SelectTrigger
                          className="h-7 w-full text-xs"
                          disabled={disabled}
                          aria-label={`第 ${index + 1} 行聚合方式`}
                        >
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="" disabled={lastMeasure}>
                            无
                          </SelectItem>
                          {AGGREGATE_TYPES.filter(
                            (aggregate) =>
                              !row.dataType || aggregateSupports(aggregate, row.dataType),
                          ).map((aggregate) => (
                            <SelectItem key={aggregate} value={aggregate}>
                              <SelectItemText>{AGGREGATE_LABEL[aggregate]}</SelectItemText>
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </td>
                  )}
                  <td className={cell}>
                    {bound ? (
                      <div className="flex items-center gap-1">
                        <span className="truncate font-mono text-[11px]">{row.standardEnCode}</span>
                        <Button
                          size="small"
                          variant="ghost"
                          disabled={disabled}
                          className="shrink-0 px-0.5 font-normal text-[#667085]"
                          onClick={() => patch(index, { standardId: null, standardEnCode: null })}
                        >
                          解绑
                        </Button>
                      </div>
                    ) : (
                      <Button
                        size="small"
                        variant="ghost"
                        disabled={disabled}
                        className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
                        onClick={() => setPicker({ mode: "single", index })}
                      >
                        绑定
                      </Button>
                    )}
                  </td>
                  <td className={cell}>
                    <Input
                      size="small"
                      variant="outlined"
                      value={row.description ?? ""}
                      maxLength={500}
                      disabled={disabled}
                      aria-label={`第 ${index + 1} 行字段描述`}
                      onChange={(event) => patch(index, { description: event.target.value })}
                    />
                  </td>
                  <td className={`${cell} text-center`}>
                    <div className="flex items-center justify-center gap-1">
                      <Button
                        size="small"
                        variant="ghost"
                        disabled={disabled || index === 0}
                        className="px-0.5 font-normal text-[#667085]"
                        aria-label={`上移第 ${index + 1} 行`}
                        onClick={() => move(index, -1)}
                      >
                        上移
                      </Button>
                      <Button
                        size="small"
                        variant="ghost"
                        disabled={disabled || index === rows.length - 1}
                        className="px-0.5 font-normal text-[#667085]"
                        aria-label={`下移第 ${index + 1} 行`}
                        onClick={() => move(index, 1)}
                      >
                        下移
                      </Button>
                      <Button
                        size="small"
                        variant="ghost"
                        disabled={disabled || lastMeasure}
                        className="px-0.5 font-normal text-[#667085] hover:text-[#d92d20]"
                        aria-label={`删除第 ${index + 1} 行`}
                        title={lastMeasure ? "汇总表至少需要一个度量字段" : undefined}
                        onClick={() => onChange(rows.filter((_, position) => position !== index))}
                      >
                        删除
                      </Button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {rows.length === 0 && (
              <tr>
                <td
                  colSpan={allowAggregate ? 12 : 11}
                  className="px-3 py-8 text-center text-[#667475]"
                >
                  暂无字段，
                  {hideImportActions ? "先添加一行" : "先添加一行、批量录入或从数据标准导入"}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {picker?.mode === "single" && (
        <StandardPicker
          mode="single"
          takenCodes={rows
            .filter((_, position) => position !== picker.index)
            .map((row) => row.enCode.trim())}
          onClose={() => setPicker(null)}
          onPick={(standards) => bindStandard(picker.index, standards[0])}
        />
      )}
      {picker?.mode === "multiple" && (
        <StandardPicker
          mode="multiple"
          takenCodes={rows.map((row) => row.enCode.trim())}
          onClose={() => setPicker(null)}
          onPick={importStandards}
        />
      )}
      {importingSource && (
        <ImportDialog
          existing={rows}
          onClose={() => setImportingSource(false)}
          onImport={(imported) => {
            onChange([...rows, ...imported]);
            setImportingSource(false);
            toast.success(`已导入 ${imported.length} 个字段`);
          }}
        />
      )}
      {importing && (
        <BatchImportDialog
          existing={rows}
          onClose={() => setImporting(false)}
          onImport={(imported) => {
            onChange([...rows, ...imported]);
            setImporting(false);
            toast.success(`已导入 ${imported.length} 个字段`);
          }}
        />
      )}
    </div>
  );
}
