import type {
  LinkUpConnectorOptionSchema,
  LinkUpConnectorSchema,
  OfflineConnectorRole,
} from '@/services/batch-link-up';

const normalizeLoose = (value: string) =>
  String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[._-]/g, '');

const DATASOURCE_OWNED_KEYS = new Set(
  [
    'url',
    'jdbcUrl',
    'jdbc_url',
    'driver',
    'driverClassName',
    'driver_class_name',
    'username',
    'user',
    'password',
    'passwd',
    'pwd',
    'schema',
    'schemaName',
    'database',
    'catalog',
    'host',
    'hosts',
    'hostname',
    'port',
    'node_urls',
    'nodeUrls',
    'fenodes',
    'http_urls',
    'jdbc_urls',
    'dialect',
    'compatible_mode',
    'properties',
    'connectionParams',
    'connection_params',
    'connection_check_timeout_sec',
    'connect_timeout_ms',
    'socket_timeout_ms',
  ].map(normalizeLoose),
);

const SOURCE_MANAGED_KEYS = new Set(
  [
    'table_path',
    'table_list',
    'query',
    'where_condition',
    'fetch_size',
  ].map(normalizeLoose),
);

const SINK_MANAGED_KEYS = new Set(
  [
    'table_path',
    'schema_save_mode',
    'data_save_mode',
    'write_mode',
    'primary_keys',
    'custom_sql',
    'batch_size',
    'dirty_data_policy',
    'dirty_data_max_count',
  ].map(normalizeLoose),
);

const CONNECTION_SCOPE_MARKERS = [
  'CONNECTION',
  'DATASOURCE',
  'CREDENTIAL',
  'SECURITY',
  'SECRET',
];

const CONNECTION_SEMANTIC_MARKERS = [
  'PASSWORD',
  'USERNAME',
  'HOST',
  'PORT',
  'URL',
  'DATABASE',
  'SCHEMA',
  'CREDENTIAL',
  'SECRET',
];

const valueKind = (option: LinkUpConnectorOptionSchema) =>
  `${option.valueType || ''} ${option.javaType || ''}`.toUpperCase();

export type ConnectorTaskOptionKind =
  | 'boolean'
  | 'number'
  | 'enum'
  | 'tags'
  | 'text';

export interface ConnectorTaskOptionField {
  option: LinkUpConnectorOptionSchema;
  kind: ConnectorTaskOptionKind;
}

const isConnectionOwned = (option: LinkUpConnectorOptionSchema) => {
  const key = normalizeLoose(option.key);
  if (DATASOURCE_OWNED_KEYS.has(key)) return true;

  const scope = String(option.scope || '').toUpperCase();
  if (CONNECTION_SCOPE_MARKERS.some((marker) => scope.includes(marker))) {
    return true;
  }

  const semanticType = String(option.semanticType || '').toUpperCase();
  return CONNECTION_SEMANTIC_MARKERS.some((marker) =>
    semanticType.includes(marker),
  );
};

const isProductManaged = (
  option: LinkUpConnectorOptionSchema,
  role: OfflineConnectorRole,
) => {
  const key = normalizeLoose(option.key);
  return role === 'SOURCE'
    ? SOURCE_MANAGED_KEYS.has(key)
    : SINK_MANAGED_KEYS.has(key);
};

export const connectorTaskOptionKind = (
  option: LinkUpConnectorOptionSchema,
): ConnectorTaskOptionKind | null => {
  const kind = valueKind(option);
  const allowedValues = Array.isArray(option.allowedValues)
    ? option.allowedValues
    : [];

  if (kind.includes('BOOLEAN') || kind.includes('BOOL')) {
    return 'boolean';
  }
  if (
    kind.includes('LIST') ||
    kind.includes('ARRAY') ||
    kind.includes('SET') ||
    kind.includes('COLLECTION')
  ) {
    return 'tags';
  }
  if (
    kind.includes('INT') ||
    kind.includes('LONG') ||
    kind.includes('DOUBLE') ||
    kind.includes('FLOAT') ||
    kind.includes('DECIMAL') ||
    kind.includes('NUMBER')
  ) {
    return 'number';
  }
  if (allowedValues.length > 0) {
    return 'enum';
  }
  if (kind.includes('MAP') || kind.includes('OBJECT')) {
    return null;
  }
  return 'text';
};

export const connectorTaskOptionFields = (
  schema: LinkUpConnectorSchema | null | undefined,
  role: OfflineConnectorRole,
): ConnectorTaskOptionField[] => {
  const options = Array.isArray(schema?.options) ? schema.options : [];

  return options
    .filter((option) => Boolean(option?.key?.trim()))
    .filter((option) => option.sensitive !== true)
    .filter((option) => !isConnectionOwned(option))
    .filter((option) => !isProductManaged(option, role))
    .map((option) => ({ option, kind: connectorTaskOptionKind(option) }))
    .filter(
      (item): item is ConnectorTaskOptionField => item.kind !== null,
    )
    .sort((left, right) => left.option.key.localeCompare(right.option.key));
};

const hasValue = (value: unknown) => {
  if (value === undefined || value === null) return false;
  if (typeof value === 'string') return value.trim().length > 0;
  if (Array.isArray(value)) return value.length > 0;
  return true;
};

export const validateRequiredConnectorTaskOptions = (
  schema: LinkUpConnectorSchema | null | undefined,
  role: OfflineConnectorRole,
  config: Record<string, any> | null | undefined,
): string[] => {
  const values =
    config?.connectorOptions && typeof config.connectorOptions === 'object'
      ? config.connectorOptions
      : {};
  const errors: string[] = [];

  for (const { option } of connectorTaskOptionFields(schema, role)) {
    if (option.required !== true || hasValue(option.defaultValue)) {
      continue;
    }

    const candidates = [option.key, ...(option.fallbackKeys || [])];
    const satisfied = candidates.some((key) => hasValue(values[key]));
    if (!satisfied) {
      errors.push(
        `${role === 'SOURCE' ? 'Source' : 'Sink'} Connector 参数 ${option.key} 不能为空`,
      );
    }
  }

  return errors;
};
