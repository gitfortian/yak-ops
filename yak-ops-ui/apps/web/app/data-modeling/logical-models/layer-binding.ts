/**
 * The warehouse layer each logical-model kind is bound to.
 *
 * <p>The layer supplies the table prefix (`dwd_`, `dim_`, …), so a detail table filed under the
 * dimension layer would ship a name that misstates where the data lives. The editor therefore
 * narrows the layer select to the kind's own layer. The match is on the layer code because that is
 * the only stable identity a workspace assigns to a layer, and it is exactly the token that ends up
 * in front of the table name.
 */
import type { ModelType } from "@/service/data-modeling/dimension-modeling/types";
import type { Layer } from "@/service/data-modeling/warehouse-planning/layers";

export const MODEL_LAYER_CODE: Record<ModelType, string> = {
  DWD: "DWD",
  DIM: "DIM",
  DWS: "DWS",
  ADS: "ADS",
};

/** True when the layer carries the code its kind is bound to; an absent layer never matches. */
export function layerMatchesType(layer: Layer | undefined, type: ModelType): boolean {
  return layer !== undefined && layer.enCode.trim().toUpperCase() === MODEL_LAYER_CODE[type];
}

/**
 * The layers the kind may live in, in the workspace's own order.
 *
 * <p>Empty means the workspace defines no layer under that code. The editor then keeps the full
 * list selectable and states the gap, because narrowing the select to nothing would leave the user
 * with no way forward on this page.
 */
export function layersForType(layers: readonly Layer[], type: ModelType): Layer[] {
  return layers.filter((layer) => layerMatchesType(layer, type));
}

/**
 * The layer to preselect when the kind changes: the first enabled match, so switching the kind
 * lands on a usable layer instead of an "unbound data source" validation error.
 */
export function defaultLayerForType(layers: readonly Layer[], type: ModelType): Layer | undefined {
  const compatible = layersForType(layers, type);
  return compatible.find((layer) => layer.enabled) ?? compatible[0];
}
