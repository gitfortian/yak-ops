import type {
  DevelopmentNode,
  DevelopmentNodeType,
  DevelopmentOutputNodeType,
  DevelopmentResourceNode,
  DevelopmentTaskType,
} from './types';

/** High-level responsibility of a node inside the data-development DAG. */
export type NodeCategory = 'PROCESSING' | 'OUTPUT';
export type NodeType = DevelopmentNodeType;
export type { DevelopmentOutputNodeType };

export const NODE_CATEGORY_BY_TYPE = {
  SQL: 'PROCESSING',
  SHELL: 'PROCESSING',
  HTTP: 'PROCESSING',
  PYTHON: 'PROCESSING',
  DATASET: 'OUTPUT',
  DATA_SERVICE: 'OUTPUT',
} as const satisfies Record<NodeType, NodeCategory>;

/** Product-approved DAG edges. Unsupported combinations fail closed. */
export const NODE_CONNECTIONS = {
  SQL: ['SQL', 'DATASET', 'DATA_SERVICE'],
  SHELL: [],
  HTTP: [],
  PYTHON: [],
  DATASET: ['DATA_SERVICE'],
  DATA_SERVICE: [],
} as const satisfies Record<NodeType, readonly NodeType[]>;

const TASK_NODE_TYPES = new Set<NodeType>(['SQL', 'SHELL', 'HTTP', 'PYTHON']);

export const getNodeCategory = (type: NodeType): NodeCategory => NODE_CATEGORY_BY_TYPE[type];

export const canConnectNodes = (source: NodeType, target: NodeType): boolean =>
  (NODE_CONNECTIONS[source] as readonly NodeType[]).includes(target);

export const isDevelopmentTaskNodeType = (type: NodeType): type is DevelopmentTaskType =>
  TASK_NODE_TYPES.has(type);

export const isDevelopmentTaskNode = (
  node: DevelopmentResourceNode,
): node is DevelopmentNode => isDevelopmentTaskNodeType(node.type);
