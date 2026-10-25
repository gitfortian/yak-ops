import { useEffect, useRef, useState } from "react";
import type { Paged } from "@/service/data-modeling/metrics/types";

interface PageState<T> {
  key: string | null;
  version: number;
  page: Paged<T> | null;
  loading: boolean;
  failed: boolean;
}

/**
 * Pages one of the five metric kinds. The loader closes over the request the caller already
 * built, so the effect only re-runs when the key or the version changes.
 */
export function useMetricPage<T extends { id: string }>(
  key: string | null,
  version: number,
  load: (signal: AbortSignal) => Promise<Paged<T>>,
) {
  const [state, setState] = useState<PageState<T>>({
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
export function useMetricOptions<T>(load: (signal: AbortSignal) => Promise<T>, version: number) {
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

/** One retry counter shared by the option loads of a dialog. */
export function useRetry() {
  const [version, setVersion] = useState(0);
  return [version, () => setVersion((value) => value + 1)] as const;
}
