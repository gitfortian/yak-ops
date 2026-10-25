import { useEffect, useRef, useState } from "react";
import {
  getCategoryTree,
  getStandards,
  initializeCatalog,
} from "@/service/data-modeling/data-standards";
import type {
  Paged,
  StandardCategory,
  StandardPage,
  StandardQuery,
} from "@/service/data-modeling/data-standards/types";

export function categoryPath(
  nodes: readonly StandardCategory[],
  id: string | null,
): StandardCategory[] | null {
  for (const node of nodes) {
    if (node.id === id) return [node];
    const nested = categoryPath(node.children, id);
    if (nested) return [node, ...nested];
  }
  return null;
}
export const pathLabel = (nodes: readonly StandardCategory[], id: string | null) =>
  categoryPath(nodes, id)
    ?.map((node) => node.name)
    .join(" / ") ?? "";
export function useCatalog() {
  const [snapshot, setSnapshot] = useState<{
    roots: StandardCategory[];
    selectedId: string | null;
  }>({ roots: [], selectedId: null });
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [version, setVersion] = useState(0);
  const initialized = useRef(false);
  const preferred = useRef<string | null | undefined>(undefined);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setFailed(false);
    const request = initialized.current ? getCategoryTree : initializeCatalog;
    void request(controller.signal)
      .then((roots) => {
        if (!active) return;
        initialized.current = true;
        const requested = preferred.current;
        preferred.current = undefined;
        setSnapshot((previous) => {
          const id = requested === undefined ? previous.selectedId : requested;
          return { roots, selectedId: categoryPath(roots, id) ? id : (roots[0]?.id ?? null) };
        });
      })
      .catch(() => {
        if (active) setFailed(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [version]);
  return {
    ...snapshot,
    loading,
    failed,
    select: (selectedId: string) => setSnapshot((previous) => ({ ...previous, selectedId })),
    refresh: (id?: string | null) => {
      preferred.current = id;
      setVersion((value) => value + 1);
    },
  };
}
interface PageState {
  query: StandardQuery | null;
  version: number;
  page: StandardPage | null;
  loading: boolean;
  failed: boolean;
}
export function useStandardPage(query: StandardQuery | null, version: number) {
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
      void getStandards(query, controller.signal)
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
interface KindPageState<T> {
  key: string | null;
  version: number;
  page: Paged<T> | null;
  loading: boolean;
  failed: boolean;
}
/**
 * Pages one of the flat standard kinds. The loader closes over the request the caller already
 * built, so the effect only re-runs when the key or the version changes.
 */
export function useKindPage<T extends { id: string }>(
  key: string | null,
  version: number,
  load: (signal: AbortSignal) => Promise<Paged<T>>,
) {
  const [state, setState] = useState<KindPageState<T>>({
    key: null,
    version: -1,
    page: null,
    loading: false,
    failed: false,
  });
  const latest = useRef(load);
  latest.current = load;
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState({ key, version, page: null, loading: key !== null, failed: false });
    if (key !== null)
      void latest
        .current(controller.signal)
        .then((page) => {
          if (active) setState({ key, version, page, loading: false, failed: false });
        })
        .catch(() => {
          if (active) setState({ key, version, page: null, loading: false, failed: true });
        });
    return () => {
      active = false;
      controller.abort();
    };
  }, [key, version]);
  const current = state.key === key && state.version === version;
  return {
    page: current ? state.page : null,
    loading: key !== null && (!current || state.loading),
    failed: current && state.failed,
  };
}
interface OptionsState<T> {
  status: "loading" | "ready" | "error";
  data: T | null;
}
/** Loads a bounded option list once per retry, for the selects a dialog needs. */
export function useOptions<T>(load: (signal: AbortSignal) => Promise<T>, version: number) {
  const [state, setState] = useState<OptionsState<T>>({ status: "loading", data: null });
  const latest = useRef(load);
  latest.current = load;
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState({ status: "loading", data: null });
    void latest
      .current(controller.signal)
      .then((data) => {
        if (active) setState({ status: "ready", data });
      })
      .catch(() => {
        if (active) setState({ status: "error", data: null });
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [version]);
  return state;
}
