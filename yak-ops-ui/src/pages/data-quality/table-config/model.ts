/**
 * @deprecated New table-registry code imports from `constants`, `types` and
 * `utils` directly. This facade protects older module-local imports.
 */
export {
  QUALITY_SOURCE_TREE_DEFAULT_WIDTH as DEFAULT_LEFT_WIDTH,
  QUALITY_SOURCE_TREE_MAX_WIDTH as MAX_LEFT_WIDTH,
  QUALITY_SOURCE_TREE_MIN_WIDTH as MIN_LEFT_WIDTH,
  QUALITY_TABLE_CANDIDATE_PAGE_SIZE as CANDIDATE_PAGE_SIZE,
  QUALITY_TABLE_PAGE_SIZE as PAGE_SIZE,
} from './constants';
export type {
  QualityDataSourceNode as DataSourceTreeNode,
  QualityDataSourceTreeKey,
} from './types';
export {
  qualityDataSourceNodeKey as dataSourceNodeKey,
  qualityTableCandidateKey as tableTargetKey,
  normalizeQualityDataSourceType as normalizeDataSourceType,
} from './utils';
