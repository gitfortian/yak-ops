import type { FormInstance } from 'antd';

export enum DataSourceOperateType {
  Create = 'CREATE',
  Edit = 'EDIT',
}

export type DataSourceId = number | string;
export type DataSourceConnectionStatus =
  | 'UNKNOWN'
  | 'CONNECTED'
  | 'DISCONNECTED'
  | string;

export interface CommonApiResponse<T> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}

export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
  pages?: number;
}

export interface DataSourceRecord {
  id?: DataSourceId;
  name?: string;
  dbType?: string;
  jdbcUrl?: string;
  environment?: string;
  environmentName?: string;
  connStatus?: DataSourceConnectionStatus;
  remark?: string;
  /** 仅详情接口返回，敏感字段使用 ****** 回显。 */
  originalJson?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DataSourcePageResult {
  bizData: DataSourceRecord[];
  pagination: PaginationInfo;
}

export interface DataSourcePageParams {
  pageNo: number;
  pageSize: number;
  dbType?: string;
  name?: string;
  keyword?: string;
  environment?: string;
  connStatus?: string;
}

export interface DataSourceSummary {
  total: number;
  connected: number;
  disconnected: number;
  unknown: number;
  environmentCount: number;
}

export interface DataSourceFormValues {
  name: string;
  environment: string;
  remark?: string;
}

export type DataSourceConnectionFormValues = Record<string, unknown>;

export interface DataSourceSavePayload extends DataSourceFormValues {
  dbType: string;
  connectionParams: string;
}

export interface DataSourceConnectTestPayload {
  dataSourceId?: DataSourceId;
  dbType?: string;
  connJson: string;
}

export interface DataSourceModalOpenPayload {
  operateType: DataSourceOperateType;
  currentRecord?: DataSourceRecord;
  onSuccess?: () => void;
  /**
   * 外部创建入口已确定 dbType 时传入。
   * 传入后弹窗会跳过数据源类型选择页。
   */
  dbType?: string;

  /**
   * 是否隐藏“上一步”按钮。
   * 从任务配置页创建来源/去向数据源时建议为 true。
   */
  hideBack?: boolean;
}

export interface DataSourceModalRef {
  open: (payload: DataSourceModalOpenPayload) => void;
  close: () => void;
}

export interface DynamicFormFieldRule {
  required?: boolean;
  pattern?: string;
  min?: number;
  max?: number;
  message: string;
}

export type DynamicFormVisibilityOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'IN'
  | 'NOT_IN'
  | 'TRUTHY'
  | 'FALSY';

/**
 * 动态字段显示条件。field 可省略，此时按 visibleWhen 顺序映射到 dependsOn。
 * 多个条件默认使用 AND 语义。
 */
export interface DynamicFormVisibilityCondition {
  field?: string;
  operator?: DynamicFormVisibilityOperator;
  value?: unknown;
  values?: unknown[];
}

/** JDBC URL 与结构化 Host / Port / Database 字段之间的双向联动描述。 */
export interface DynamicFormJdbcUrlLinkage {
  /** 例如 jdbc:mysql://{host}:{port}/{database}。 */
  template: string;
  hostField?: string;
  portField?: string;
  databaseField?: string;
  /** 默认保留 URL 尾部的 ?query 或 ;properties。 */
  preserveSuffix?: boolean;
}

export type SshAuthType = 'PASSWORD' | 'PRIVATE_KEY';

/** 标准 SSH 隧道配置值，由 SSH Schema 组件统一消费。 */
export interface SshTunnelConfigValue {
  enabled?: boolean;
  host?: string;
  port?: number;
  username?: string;
  authType?: SshAuthType;
  password?: string;
  privateKey?: string;
  passphrase?: string;
  strictHostKeyChecking?: boolean;
  knownHosts?: string;
}

export type DynamicFormFieldType =
  | 'INPUT'
  | 'PASSWORD'
  | 'SELECT'
  | 'NUMBER'
  | 'SWITCH'
  | 'TEXTAREA'
  | 'CUSTOM_SELECT'
  | 'DRIVER'
  | 'SSH'
  | 'JDBC_URL';

export interface DynamicFormField {
  key: string;
  label: string;
  type: DynamicFormFieldType;
  placeholder?: string;
  options?: Array<{ label: string; value: string | number }>;
  defaultValue?: unknown;
  rules?: DynamicFormFieldRule[];
  /** 声明联动依赖；条件中显式引用的字段会自动补充到此列表。 */
  dependsOn?: string[];
  /** 字段显示条件；支持单条件或多条件，多条件按 AND 计算。 */
  visibleWhen?:
    | DynamicFormVisibilityCondition
    | DynamicFormVisibilityCondition[];
  /** JDBC_URL 标准组件使用的双向联动元数据。 */
  urlLinkage?: DynamicFormJdbcUrlLinkage;
}

/**
 * 插件动态表单分区。
 *
 * collapsible=false 时直接展示；collapsible=true 时由前端渲染为 Collapse。
 * defaultExpanded 仅对可折叠分区生效，未配置时默认展开，避免必填字段被静默隐藏。
 */
export interface DynamicFormSection {
  key: string;
  title: string;
  description?: string;
  collapsible?: boolean;
  defaultExpanded?: boolean;
  fields: DynamicFormField[];
}

export interface DynamicFormSchemaResponse {
  pluginType?: string;
  /** 新版分区 Schema，存在有效分区时优先使用。 */
  sections?: DynamicFormSection[];
  /** 兼容旧插件的扁平字段列表。 */
  formFields?: DynamicFormField[];
  installRequired?: boolean;
  installHint?: string;
}

export interface DynamicDataSourceFormProps {
  dbType: string;
  form: FormInstance<DataSourceFormValues>;
  configForm: FormInstance;
  operateType: DataSourceOperateType;
  /** 编辑模式下的初始配置数据。 */
  initialConfig?: Record<string, unknown>;
}

export interface DataSourceOptionItem {
  label: string;
  value: string;
}

export interface DataSourceCatalogItem {
  onlyDiScript: boolean;
  dbType: string;
  type: string;
  connectorType?: string;
  disabled?: boolean;
  img?: string;
  doc?: {
    reader?: string;
    writer?: string;
  };
}

export interface DataSourceGroup {
  groupName: string;
  datasourceList: DataSourceCatalogItem[];
}
