/**
 * The layer choices the reverse-modeling wizard makes for itself.
 *
 * <p>Kept free of runtime service imports so the two decisions that produced the illegal default can
 * be tested directly: which layer the wizard preselects and which ones it lets the user pick. The
 * kind-to-layer binding itself stays in `logical-models/layer-binding`, so this page and the editor
 * cannot state two different rules about where a kind may live.
 */
import type { ModelType } from "@/service/data-modeling/dimension-modeling/types";
import type { Layer } from "@/service/data-modeling/warehouse-planning/layers";
// The extension keeps this module loadable by the Node test runner; the bundler resolves it too.
import { defaultLayerForType, layerMatchesType } from "../../logical-models/layer-binding.ts";

/** Why a layer is not pickable, or null when the user may choose it. */
export type LayerBlock = "unenabled" | "mismatch";

/**
 * The layer the wizard preselects for a kind: the enabled one carrying that kind's code.
 *
 * <p>A disabled match is refused, not used, because an unenabled layer binds no datasource and the
 * server rejects the save. Returning nothing when the workspace defines no layer for the kind is what
 * lets the page state the reason instead of storing a model the editor immediately flags.
 */
export function defaultReverseLayer(layers: readonly Layer[], type: ModelType): Layer | undefined {
  const suggested = defaultLayerForType(layers, type);
  return suggested?.enabled ? suggested : undefined;
}

/**
 * Whether this layer may be chosen for the kind.
 *
 * <p>`mismatch` is only reported when the workspace actually defines the kind's own layer. With none
 * defined, disabling every option would strand the user on this page, so the list stays as the
 * forward editor leaves it and the caller states the gap.
 */
export function layerBlock(
  layer: Layer,
  type: ModelType,
  hasLayerForKind: boolean,
): LayerBlock | null {
  if (!layer.enabled) return "unenabled";
  if (hasLayerForKind && !layerMatchesType(layer, type)) return "mismatch";
  return null;
}
