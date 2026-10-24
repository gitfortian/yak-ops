import { useEffect, useState } from "react";
import { getOwners, getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import { getLayers, type Layer } from "@/service/data-modeling/warehouse-planning/layers";
import type { OwnerOption, PlanningTree } from "@/service/data-modeling/warehouse-planning/types";

interface OptionsState {
  layers: Layer[];
  tree: PlanningTree;
  owners: OwnerOption[];
  status: "loading" | "ready" | "failed";
}

const EMPTY_TREE: PlanningTree = { categories: [], datamarts: [] };

/** Layers, planning tree and workspace members the editor needs before it can validate anything. */
export function useModelOptions() {
  const [state, setState] = useState<OptionsState>({
    layers: [],
    tree: EMPTY_TREE,
    owners: [],
    status: "loading",
  });
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState((previous) => ({ ...previous, status: "loading" }));
    void Promise.all([
      getLayers(controller.signal),
      getPlanningTree(controller.signal),
      getOwners(controller.signal),
    ])
      .then(([layers, tree, owners]) => {
        if (active) setState({ layers, tree, owners, status: "ready" });
      })
      .catch(() => {
        if (active) setState((previous) => ({ ...previous, status: "failed" }));
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [version]);
  return {
    ...state,
    reload: () => setVersion((value) => value + 1),
  };
}
