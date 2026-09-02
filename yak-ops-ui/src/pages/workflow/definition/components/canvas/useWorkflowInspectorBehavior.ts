import type { PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';

const DEFAULT_PANEL_WIDTH = 380;
const MIN_PANEL_WIDTH = 320;
const MAX_PANEL_WIDTH = 640;
const MIN_CANVAS_WIDTH = 360;
const PANEL_WIDTH_STORAGE_KEY = 'yak.workflow.inspector.width';

const readStoredPanelWidth = (fallback: number) => {
  if (typeof window === 'undefined') return fallback;
  const value = Number(window.localStorage.getItem(PANEL_WIDTH_STORAGE_KEY));
  if (!Number.isFinite(value)) return fallback;
  return Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, value));
};

const useWorkflowInspectorBehavior = (defaultWidth = DEFAULT_PANEL_WIDTH) => {
  const [panelWidth, setPanelWidth] = useState(() => readStoredPanelWidth(defaultWidth));
  const [resizing, setResizing] = useState(false);
  const cleanupResizeRef = useRef<(() => void) | undefined>(undefined);

  useEffect(() => {
    const keepInspectorOpen = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Element) || !target.closest('.react-flow__pane')) return;

      // React Flow clears node selection when the pane receives a click. The workflow
      // inspector intentionally behaves like Dify: clicking empty canvas keeps the
      // current inspector open, and only its explicit close button dismisses it.
      event.stopPropagation();
      event.stopImmediatePropagation();
    };

    document.addEventListener('click', keepInspectorOpen, true);
    return () => document.removeEventListener('click', keepInspectorOpen, true);
  }, []);

  useEffect(() => () => cleanupResizeRef.current?.(), []);

  useEffect(() => {
    const miniMap = document.querySelector('.react-flow__minimap');
    if (!(miniMap instanceof HTMLElement)) return undefined;

    miniMap.style.setProperty('right', `${Math.round(panelWidth) + 24}px`, 'important');
    return () => miniMap.style.removeProperty('right');
  }, [panelWidth]);

  const handleResizePointerDown = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.stopPropagation();
    cleanupResizeRef.current?.();

    const startX = event.clientX;
    const startWidth = panelWidth;
    const flowBounds = document.querySelector('.react-flow')?.getBoundingClientRect();
    const maxWidth = flowBounds
      ? Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, flowBounds.width - MIN_CANVAS_WIDTH))
      : MAX_PANEL_WIDTH;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    const handlePointerMove = (moveEvent: PointerEvent) => {
      const nextWidth = startWidth + startX - moveEvent.clientX;
      setPanelWidth(Math.min(maxWidth, Math.max(MIN_PANEL_WIDTH, nextWidth)));
    };

    const cleanup = () => {
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', cleanup);
      window.removeEventListener('pointercancel', cleanup);
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      setResizing(false);
      cleanupResizeRef.current = undefined;
    };

    cleanupResizeRef.current = cleanup;
    setResizing(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', cleanup);
    window.addEventListener('pointercancel', cleanup);
  }, [panelWidth]);

  useEffect(() => {
    window.localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(Math.round(panelWidth)));
  }, [panelWidth]);

  return {
    panelWidth,
    resizing,
    handleResizePointerDown,
  };
};

export default useWorkflowInspectorBehavior;
