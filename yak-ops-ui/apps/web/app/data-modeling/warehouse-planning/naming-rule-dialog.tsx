import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Spinner,
  Switch,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useRef, useState } from "react";
import {
  getNamingRule,
  layerFailureMessage,
  updateNamingRule,
  type Layer,
  type LayerNamingRule,
} from "@/service/data-modeling/warehouse-planning/layers";

/** Enables or disables one layer's suffix rule and shows what still violates it. */
export default function NamingRuleDialog(props: {
  layer: Layer;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [rule, setRule] = useState<LayerNamingRule | null>(null);
  const [enabled, setEnabled] = useState(props.layer.namingRuleEnabled);
  const [acknowledged, setAcknowledged] = useState(false);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [version, setVersion] = useState(0);
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState("");
  const active = useRef(true),
    busy = useRef(false);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    setStatus("loading");
    void getNamingRule(props.layer.id, controller.signal)
      .then((row) => {
        if (!current) return;
        setRule(row);
        setEnabled(row.enabled);
        setAcknowledged(false);
        setStatus("ready");
      })
      .catch(() => {
        if (current) setStatus("error");
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [props.layer.id, version]);
  const violations = rule?.violations ?? [];
  // Enabling over existing violations needs an explicit confirmation; the models stay listed.
  const blocked = enabled && violations.length > 0 && !acknowledged;
  const save = async () => {
    if (busy.current || !rule || blocked) return;
    busy.current = true;
    setSaving(true);
    setFailure("");
    try {
      await updateNamingRule(props.layer.id, {
        enabled,
        acknowledgeViolations: acknowledged,
        revision: rule.revision,
      });
      if (active.current) {
        toast.success("命名规则已保存");
        props.onSaved();
      }
    } catch (error) {
      if (active.current) setFailure(layerFailureMessage(error));
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
      <DialogContent className="max-h-[86vh] w-[620px] overflow-auto rounded-xl p-5 shadow-none">
        <DialogTitle className="text-sm font-medium">
          命名规则 · {props.layer.cnName}（{props.layer.enCode}）
        </DialogTitle>
        <DialogDescription className="mt-2 text-xs leading-5 text-[var(--yak-color-text-secondary)]">
          启用后，该分层下新建或修改的明细表与汇总表，表名后缀必须是
          “词根_业务过程编码”，可再跟一个“_限定词”；词根取自命名词典，总长度不超过 41 个字符。
          维度表、应用表与集市模型不受此规则约束。
        </DialogDescription>
        {status === "error" && (
          <Alert>
            命名规则读取失败
            <Button size="small" onClick={() => setVersion((value) => value + 1)}>
              重试
            </Button>
          </Alert>
        )}
        {status === "loading" && <Spinner />}
        {status === "ready" && rule && (
          <div className="mt-4 space-y-3">
            <label className="flex items-center gap-3 text-sm">
              <Switch
                size="small"
                aria-label={enabled ? "停用命名规则" : "启用命名规则"}
                checked={enabled}
                loading={saving}
                disabled={saving}
                onCheckedChange={(checked) => {
                  setEnabled(checked);
                  setFailure("");
                }}
              />
              {enabled ? "已启用" : "未启用"}
            </label>
            <div>
              <p className="text-xs font-medium">当前不符合规则的模型（{violations.length}）</p>
              {violations.length === 0 ? (
                <p className="mt-1 text-xs text-[var(--yak-color-text-secondary)]">
                  没有不符合规则的模型。
                </p>
              ) : (
                <ul className="mt-2 max-h-48 space-y-1 overflow-auto text-xs text-[var(--yak-components-field-label)]">
                  {violations.map((row) => (
                    <li key={row.modelId} className="flex min-w-0 items-center gap-2">
                      <Badge className="shrink-0">{row.type}</Badge>
                      <span className="truncate" title={row.name}>
                        {row.name} · {row.cnName}
                      </span>
                      {row.processCode && (
                        <span className="shrink-0 text-[var(--yak-components-muted-text)]">
                          过程 {row.processCode}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
            {enabled && violations.length > 0 && (
              <label className="flex items-start gap-2 text-xs text-[var(--yak-components-field-label)]">
                <Checkbox
                  checked={acknowledged}
                  disabled={saving}
                  onCheckedChange={(checked) => setAcknowledged(checked === true)}
                />
                <span>
                  我已查看上述模型。启用后它们不会被自动改名，但在改成合规名称前无法再次保存。
                </span>
              </label>
            )}
            {failure && <Alert>{failure}</Alert>}
          </div>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={saving} onClick={props.onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            loading={saving}
            disabled={status !== "ready" || blocked}
            onClick={() => void save()}
          >
            保存
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
