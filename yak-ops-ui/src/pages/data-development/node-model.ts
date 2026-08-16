import type { DevelopmentTaskType } from './types';

/** High-level responsibility of a node inside the data-development DAG. */
export type NodeCategory = 'PROCESSING' | 'OUTPUT';

/** Output nodes are reserved in phase 1 and become creatable DAG nodes in phase 2. */
export type DevelopmentOutputNodeType = 'DATASET' | 'DATA_SERVICE';

/**
 * Domain-level node type used by the data-development DAG.
 *
 * Existing authoring task types remain processing nodes. Dataset and Data Service are modeled now,
 * but this phase intentionally does not expose them in the node palette or persistence workflow.
 */
export type NodeType = DevelopmentTaskType | DevelopmentOutputNodeType;

export const NODE_CATEGORY_BY_TYPE = {
  SQL: 'PROCESSING',
  SHELL: 'PROCESSING',
  HTTP: 'PROCESSING',
  PYTHON: 'PROCESSING',
  DATASET: 'OUTPUT',
  DATA_SERVICE: 'OUTPUT',
} as const satisfies Record<NodeType, NodeCategory>;

/**
 * Phase-1 DAG contract. Only product-approved edges are declared here; unsupported edges fail closed.
 * Additional processing-node edges can be added when their DAG semantics are explicitly designed.
 */
export const NODE_CONNECTIONS = {
  SQL: ['SQL', 'DATASET', 'DATA_SERVICE'],
  SHELL: [],
  HTTP: [],
  PYTHON: [],
  DATASET: ['DATA_SERVICE'],
  DATA_SERVICE: [],
} as const satisfies Record<NodeType, readonly NodeType[]>;

export const getNodeCategory = (type: NodeType): NodeCategory => NODE_CATEGORY_BY_TYPE[type];

export const canConnectNodes = (source: NodeType, target: NodeType): boolean =>
  (NODE_CONNECTIONS[source] as readonly NodeType[]).includes(target);
