import type { Edge } from 'reactflow';

export const defaultDagPosition = (index: number) => ({
  x: 80 + (index % 3) * 300,
  y: 80 + Math.floor(index / 3) * 150,
});

/** Returns true if source -> target would make the current directed graph cyclic. */
export const wouldCreateCycle = (
  edges: Pick<Edge, 'source' | 'target'>[],
  source: string,
  target: string,
): boolean => {
  if (source === target) return true;
  const outgoing = new Map<string, string[]>();
  edges.forEach((edge) => {
    outgoing.set(edge.source, [...(outgoing.get(edge.source) || []), edge.target]);
  });

  const pending = [target];
  const visited = new Set<string>();
  while (pending.length) {
    const current = pending.pop();
    if (!current || visited.has(current)) continue;
    if (current === source) return true;
    visited.add(current);
    pending.push(...(outgoing.get(current) || []));
  }
  return false;
};
