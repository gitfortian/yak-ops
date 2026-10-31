import { Alert, Button, Dialog, DialogContent, DialogTitle, Badge } from "@yak-ops/yak-ui";
import type { GovernanceScanDetail } from "@/service/data-governance/asset-governance/types";

import {
  governanceRuleLabel,
  GOVERNANCE_SCAN_STATUS_LABEL,
  GOVERNANCE_SCAN_STATUS_TONE,
} from "./labels";
import { formatScanDuration } from "./scan-format";

interface Props {
  detail: GovernanceScanDetail | undefined;
  onClose: () => void;
}

/** 扫描详情弹框：运行摘要与每条规则的结果。 */
export default function ScanDetailDialog({ detail, onClose }: Props) {
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="w-[560px]">
        <DialogTitle className="text-[15px] font-semibold">扫描详情</DialogTitle>

        {!detail ? (
          <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            加载中
          </div>
        ) : (
          <>
            <dl className="mt-4 grid grid-cols-3 gap-x-4 gap-y-3">
              <div>
                <dt className="text-xs text-[var(--yak-components-muted-text)]">状态</dt>
                <dd className="mt-1">
                  <Badge tone={GOVERNANCE_SCAN_STATUS_TONE[detail.scan.status]}>
                    {GOVERNANCE_SCAN_STATUS_LABEL[detail.scan.status]}
                  </Badge>
                </dd>
              </div>
              <div>
                <dt className="text-xs text-[var(--yak-components-muted-text)]">触发</dt>
                <dd className="mt-1 text-sm text-[var(--yak-components-panel-text)]">
                  {detail.scan.triggerType === "MANUAL" ? "手动" : "定时"}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-[var(--yak-components-muted-text)]">耗时</dt>
                <dd className="mt-1 text-sm text-[var(--yak-components-panel-text)]">
                  {formatScanDuration(detail.scan.durationMillis)}
                </dd>
              </div>
            </dl>

            <div className="mt-4 overflow-hidden rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)]">
              <table className="w-full text-[13px]">
                <thead>
                  <tr className="border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)] text-left text-xs text-[var(--yak-components-muted-text)]">
                    <th className="px-3 py-2 font-medium">规则</th>
                    <th className="px-3 py-2 font-medium">结果</th>
                    <th className="w-20 px-3 py-2 text-right font-medium">发现</th>
                    <th className="w-16 px-3 py-2 text-right font-medium">新增</th>
                    <th className="w-16 px-3 py-2 text-right font-medium">解决</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.rules.map((rule) => (
                    <tr
                      key={rule.ruleCode}
                      className="border-b border-[var(--yak-datamap-divider-soft)] last:border-b-0"
                    >
                      <td className="px-3 py-2 text-[var(--yak-components-panel-text)]">
                        {governanceRuleLabel(rule.ruleCode)}
                      </td>
                      <td className="px-3 py-2">
                        <Badge tone={rule.status === "SUCCEEDED" ? "success" : "danger"}>
                          {rule.status === "SUCCEEDED" ? "完成" : "失败"}
                        </Badge>
                      </td>
                      <td className="px-3 py-2 text-right text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
                        {rule.findingCount}
                      </td>
                      <td className="px-3 py-2 text-right text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
                        {rule.issueCreatedCount}
                      </td>
                      <td className="px-3 py-2 text-right text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
                        {rule.issueResolvedCount}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        <div className="mt-5 flex justify-end">
          <Button size="small" onClick={onClose}>
            关闭
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

export function ScanErrorAlert({ message }: { message: string }) {
  return <Alert>{message}</Alert>;
}
