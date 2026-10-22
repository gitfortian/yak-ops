import { useEffect, useRef, useState } from "react";
import { getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import type {
  CategoryNode,
  DomainNode,
  NodeKey,
  PlanningNode,
} from "@/service/data-modeling/warehouse-planning/types";

export const nodeKey = (node: Pick<PlanningNode, "kind" | "id">): NodeKey =>
  `${node.kind}:${node.id}`;

export function findNode(nodes: readonly PlanningNode[], key: NodeKey | null): PlanningNode | null {
  for (const node of nodes) {
    if (nodeKey(node) === key) return node;
    const child = findNode(node.children, key);
    if (child) return child;
  }
  return null;
}

export function findParent(
  nodes: readonly CategoryNode[],
  node: PlanningNode,
): CategoryNode | DomainNode | null {
  if (node.kind === "CATEGORY") return null;
  const kind = node.kind === "DOMAIN" ? "CATEGORY" : "DOMAIN";
  const parent = findNode(nodes, `${kind}:${node.parentId}`);
  return parent && parent.kind !== "PROCESS" ? parent : null;
}

export function ancestorKeys(
  nodes: readonly PlanningNode[],
  key: NodeKey | null,
): NodeKey[] | null {
  for (const node of nodes) {
    if (nodeKey(node) === key) return [];
    const path = ancestorKeys(node.children, key);
    if (path) return [nodeKey(node), ...path];
  }
  return null;
}

export function usePlanning() {
  const [snapshot, setSnapshot] = useState<{
    roots: CategoryNode[];
    selectedKey: NodeKey | null;
  }>({ roots: [], selectedKey: null });
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [version, setVersion] = useState(0);
  const preferredKey = useRef<NodeKey | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setFailed(false);
    void getPlanningTree(controller.signal)
      .then((roots) => {
        if (!active) return;
        const preferred = preferredKey.current;
        preferredKey.current = null;
        setSnapshot((previous) => {
          const requested = preferred ?? previous.selectedKey;
          return {
            roots,
            selectedKey: findNode(roots, requested)
              ? requested
              : roots[0]
                ? nodeKey(roots[0])
                : null,
          };
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
    selected: findNode(snapshot.roots, snapshot.selectedKey),
    select: (selectedKey: NodeKey) => setSnapshot((previous) => ({ ...previous, selectedKey })),
    refresh: (preferred?: NodeKey) => {
      if (preferred) preferredKey.current = preferred;
      setVersion((value) => value + 1);
    },
  };
}
