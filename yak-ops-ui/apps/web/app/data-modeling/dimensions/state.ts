import { useEffect, useState } from "react";
import { getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import type { PlanningTree } from "@/service/data-modeling/warehouse-planning/types";
import { getDimensions } from "@/service/data-modeling/dimension-modeling";
import type {
  ConceptDimension,
  DimensionFilter,
} from "@/service/data-modeling/dimension-modeling/types";

const EMPTY_TREE: PlanningTree = { categories: [], datamarts: [] };

/** Only the planning tree is needed here; domains and data marts are the two ownership scopes. */
export function usePlanningTree() {
  const [tree, setTree] = useState<PlanningTree>(EMPTY_TREE);
  const [status, setStatus] = useState<"loading" | "ready" | "failed">("loading");
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setStatus("loading");
    void getPlanningTree(controller.signal)
      .then((value) => {
        if (active) {
          setTree(value);
          setStatus("ready");
        }
      })
      .catch(() => {
        if (active) setStatus("failed");
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [version]);
  return { tree, status, reload: () => setVersion((value) => value + 1) };
}

interface ListState {
  filter: DimensionFilter | null;
  version: number;
  items: ConceptDimension[];
  loading: boolean;
  failed: boolean;
}

export function useDimensionList(filter: DimensionFilter | null, version: number) {
  const [state, setState] = useState<ListState>({
    filter: null,
    version: -1,
    items: [],
    loading: false,
    failed: false,
  });
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState({ filter, version, items: [], loading: filter !== null, failed: false });
    if (filter)
      void getDimensions(filter, controller.signal)
        .then((items) => {
          if (active) setState({ filter, version, items, loading: false, failed: false });
        })
        .catch(() => {
          if (active) setState({ filter, version, items: [], loading: false, failed: true });
        });
    return () => {
      active = false;
      controller.abort();
    };
  }, [filter, version]);
  const current = state.filter === filter && state.version === version;
  return {
    items: current ? state.items : [],
    loading: filter !== null && (!current || state.loading),
    failed: current && state.failed,
  };
}
