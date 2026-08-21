export type ReleaseState = 'DRAFT' | 'PUBLISHED';
export type DesiredState = 'RUNNING' | 'STOPPED';
export type ObservedState = 'STOPPED' | 'STARTING' | 'RUNNING' | 'STOPPING' | 'FAILED' | 'UNKNOWN' | 'CONFLICT';

export interface TableRoute {
  sourceTable: string;
  sinkTable: string;
  matchMode: 'EXACT' | 'REGEX';
  keyColumns: string[];
}

export interface CdcPipelineSpec {
  sourceDataSourceRef: number;
  sinkDataSourceRef: number;
  tables: TableRoute[];
  startupMode: 'initial' | 'latest-offset';
  schemaEvolution: 'EVOLVE' | 'IGNORE' | 'FAIL';
  parallelism: number;
  checkpointIntervalMs: number;
  restart: {
    strategy: 'fixed-delay' | 'failure-rate' | 'none';
    attempts: number;
    delayMs: number;
  };
  sink: {
    maxRetries: number;
    batchSize: number;
    flushIntervalMs: number;
    maxBatchBytes: number;
    statementCacheSize: number;
    strictReplaySafety: boolean;
  };
}

export interface RealtimeDeployment {
  id: number;
  definitionVersion: number;
  specSummary?: string;
  configDigest: string;
  idempotencyKey: string;
  engineJobId?: string;
  runtimeRevision?: string;
  status: string;
  resultUncertain: boolean;
  errorMessage?: string;
  createTime: string;
  updateTime: string;
}

export interface RealtimeJob {
  id: number;
  name: string;
  description?: string;
  spec?: CdcPipelineSpec;
  releaseState: ReleaseState;
  desiredState: DesiredState;
  observedState: ObservedState;
  definitionVersion: number;
  publishedVersion?: number;
  configDigest: string;
  lastError?: string;
  createTime: string;
  updateTime: string;
  latestDeployment?: RealtimeDeployment;
}

export interface RealtimeJobPage {
  records: RealtimeJob[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface RealtimeEvent {
  id: number;
  deploymentId?: number;
  eventType: string;
  fromState?: string;
  toState?: string;
  message?: string;
  createTime: string;
}

export interface RealtimeJobChange {
  definitionId: number;
  eventType: string;
  fromState?: string;
  toState?: string;
  message?: string;
}

export interface DataSourceOption {
  label: string;
  value: string;
  dbType: string;
}

export interface RuntimeCapabilities {
  runtimeVersion?: string;
  javaVersion?: string;
  flinkVersion?: string;
  flinkCdcVersion?: string;
  deliverySemantics?: string;
  connectors?: {
    sources?: string[];
    sinks?: string[];
    schemaEvolution?: string[];
  };
  checkpointsApi?: boolean;
  metricsApi?: boolean;
  checkpointConfiguration?: boolean;
  restartConfiguration?: boolean;
  dynamicCredentialBinding?: boolean;
  protocolVersion?: string;
  protocolCompatible?: boolean;
  deployEnabled?: boolean;
  deployDisabledReason?: string;
}

export interface ApiResponse<T> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}
