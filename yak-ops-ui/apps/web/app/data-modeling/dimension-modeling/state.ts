import { useEffect, useState } from "react";
import { getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import type { PlanningTree } from "@/service/data-modeling/warehouse-planning/types";
import { getDimensions, getModelCatalog } from "@/service/data-modeling/dimension-modeling";
import type {
  ConceptDimension,
  LogicalModel,
} from "@/service/data-modeling/dimension-modeling/types";

export interface CatalogState {
  tree: PlanningTree;
  dimensions: ConceptDimension[];
  models: LogicalModel[];
  truncated: boolean;
  status: "loading" | "ready" | "failed";
}

const EMPTY: CatalogState = {
  tree: { categories: [], datamarts: [] },
  dimensions: [],
  models: [],
  truncated: false,
  status: "loading",
};

/**
 * One read of the planning tree, the concept dimensions and the Workspace models.
 *
 * <p>The tree attaches tables to dimensions and processes, so it needs every model up front instead
 * of one request per node; the catalog reports when the page cap stopped it early.
 */
export function useDimensionModelingCatalog() {
  const [state, setState] = useState<CatalogState>(EMPTY);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState((previous) => ({ ...previous, status: "loading" }));
    void Promise.all([
      getPlanningTree(controller.signal),
      getDimensions({ scope: "ALL", scopeId: "" }, controller.signal),
      getModelCatalog(controller.signal),
    ])
      .then(([tree, dimensions, catalog]) => {
        if (active)
          setState({
            tree,
            dimensions,
            models: catalog.items,
            truncated: catalog.truncated,
            status: "ready",
          });
      })
      .catch(() => {
        if (active) setState((previous) => ({ ...previous, status: "failed" }));
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [version]);
  return { ...state, reload: () => setVersion((value) => value + 1) };
}
