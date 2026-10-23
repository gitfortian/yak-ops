import { useEffect, useState } from "react";
import { getOwners, getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import { getLayers, type Layer } from "@/service/data-modeling/warehouse-planning/layers";
import type { OwnerOption, PlanningTree } from "@/service/data-modeling/warehouse-planning/types";
import { getModels } from "@/service/data-modeling/dimension-modeling";
import type { ModelPage, ModelQuery } from "@/service/data-modeling/dimension-modeling/types";

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

interface PageState {
  query: ModelQuery | null;
  version: number;
  page: ModelPage | null;
  loading: boolean;
  failed: boolean;
}

export function useModelPage(query: ModelQuery | null, version: number) {
  const [state, setState] = useState<PageState>({
    query: null,
    version: -1,
    page: null,
    loading: false,
    failed: false,
  });
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState({ query, version, page: null, loading: query !== null, failed: false });
    if (query)
      void getModels(query, controller.signal)
        .then((page) => {
          if (active) setState({ query, version, page, loading: false, failed: false });
        })
        .catch(() => {
          if (active) setState({ query, version, page: null, loading: false, failed: true });
        });
    return () => {
      active = false;
      controller.abort();
    };
  }, [query, version]);
  const current = state.query === query && state.version === version;
  return {
    page: current ? state.page : null,
    loading: query !== null && (!current || state.loading),
    failed: current && state.failed,
  };
}
