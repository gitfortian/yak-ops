import { useEffect, useRef, useState } from "react";
import { getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import type {
  NodeKey,
  NodeKind,
  PlanningNode,
  PlanningTree,
} from "@/service/data-modeling/warehouse-planning/types";

const EMPTY_TREE: PlanningTree = { categories: [], datamarts: [] };

/** Parent level of every level; null marks a root of one of the two trees. */
const PARENT_KIND: Record<NodeKind, NodeKind | null> = {
  CATEGORY: null,
  DOMAIN: "CATEGORY",
  PROCESS: "DOMAIN",
  DATASET: null,
  SUBJECT: "DATASET",
};

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

export function findNodeInTree(tree: PlanningTree, key: NodeKey | null): PlanningNode | null {
  return findNode(tree.categories, key) ?? findNode(tree.datamarts, key);
}

export function findParent(tree: PlanningTree, node: PlanningNode): PlanningNode | null {
  const parentKind = PARENT_KIND[node.kind];
  if (!parentKind) return null;
  return findNodeInTree(tree, `${parentKind}:${node.parentId}`);
}

/** Ancestors from the tree root down to (excluding) the node, for breadcrumbs. */
export function pathToNode(tree: PlanningTree, key: NodeKey | null): PlanningNode[] {
  const walk = (nodes: readonly PlanningNode[]): PlanningNode[] | null => {
    for (const node of nodes) {
      if (nodeKey(node) === key) return [node];
      const below = walk(node.children);
      if (below) return [node, ...below];
    }
    return null;
  };
  const path = walk([...tree.categories, ...tree.datamarts]);
  return path ? path.slice(0, -1) : [];
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

function firstKey(tree: PlanningTree): NodeKey | null {
  const root = tree.categories[0] ?? tree.datamarts[0];
  return root ? nodeKey(root) : null;
}

export function usePlanning() {
  const [snapshot, setSnapshot] = useState<{
    tree: PlanningTree;
    selectedKey: NodeKey | null;
  }>(() => ({ tree: EMPTY_TREE, selectedKey: null }));
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
      .then((tree) => {
        if (!active) return;
        const preferred = preferredKey.current;
        preferredKey.current = null;
        setSnapshot((previous) => {
          const requested = preferred ?? previous.selectedKey;
          return {
            tree,
            selectedKey: findNodeInTree(tree, requested) ? requested : firstKey(tree),
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
    selected: findNodeInTree(snapshot.tree, snapshot.selectedKey),
    select: (selectedKey: NodeKey) => setSnapshot((previous) => ({ ...previous, selectedKey })),
    refresh: (preferred?: NodeKey) => {
      if (preferred) preferredKey.current = preferred;
      setVersion((value) => value + 1);
    },
  };
}
