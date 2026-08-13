import type { Rule } from 'antd/es/form';

import type {
  DynamicFormField,
  DynamicFormFieldRule,
  DynamicFormSchemaResponse,
  DynamicFormSection,
  DynamicFormVisibilityCondition,
  DynamicFormVisibilityOperator,
} from '../../../types';

export const transformRules = (
  rules: DynamicFormFieldRule[] | undefined,
  fieldType?: DynamicFormField['type'],
): Rule[] => {
  if (!rules) return [];

  return rules.map((rule) => {
    const formRule: Rule = {
      message: rule.message,
      ...(fieldType === 'NUMBER' ? { type: 'number' as const } : {}),
    };
    if (rule.required === true) formRule.required = true;
    if (typeof rule.min === 'number') formRule.min = rule.min;
    if (typeof rule.max === 'number') formRule.max = rule.max;
    if (rule.pattern) {
      try {
        formRule.pattern = new RegExp(rule.pattern);
      } catch {
        // 后端插件配置错误时不让整个表单崩溃，服务端仍会再次校验。
      }
    }
    return formRule;
  });
};

export const getConfigInitialValues = (fields: DynamicFormField[]) => {
  const initialValues: Record<string, unknown> = {};
  fields.forEach((field) => {
    initialValues[field.key] = getFieldDefaultValue(field);
  });
  return initialValues;
};

export const getFieldDefaultValue = (field: DynamicFormField) => {
  const value = field.defaultValue;
  if (value === undefined || value === null || value === '') {
    return field.type === 'CUSTOM_SELECT' ? [] : value;
  }

  switch (field.type) {
    case 'NUMBER':
      return Number(value);
    case 'SWITCH':
      return typeof value === 'boolean' ? value : value === 'true';
    case 'CUSTOM_SELECT':
      if (Array.isArray(value)) return value;
      if (typeof value === 'string') {
        try {
          const parsed = JSON.parse(value);
          return Array.isArray(parsed) ? parsed : [];
        } catch {
          return [];
        }
      }
      return [];
    default:
      return value;
  }
};

const normalizeOperator = (
  operator?: DynamicFormVisibilityOperator | string,
  condition?: DynamicFormVisibilityCondition,
): DynamicFormVisibilityOperator => {
  const value = operator?.trim().toUpperCase();
  if (
    value === 'EQUALS' ||
    value === 'NOT_EQUALS' ||
    value === 'IN' ||
    value === 'NOT_IN' ||
    value === 'TRUTHY' ||
    value === 'FALSY'
  ) {
    return value;
  }
  return condition && 'value' in condition ? 'EQUALS' : 'TRUTHY';
};

const normalizeVisibilityConditions = (
  field: DynamicFormField,
): DynamicFormVisibilityCondition[] => {
  const raw = field.visibleWhen;
  if (!raw) return [];
  const conditions = Array.isArray(raw) ? raw : [raw];
  const dependencies = field.dependsOn || [];

  return conditions.map((condition, index) => ({
    ...condition,
    field:
      condition.field?.trim() || dependencies[index]?.trim() || dependencies[0]?.trim(),
    operator: normalizeOperator(condition.operator, condition),
  }));
};

/**
 * 归一字段联动依赖：显式 dependsOn 与 visibleWhen 中引用的字段会合并去重。
 */
export const getFieldDependencies = (field: DynamicFormField): string[] => {
  const conditions = normalizeVisibilityConditions(field);
  return Array.from(
    new Set(
      [...(field.dependsOn || []), ...conditions.map((condition) => condition.field)]
        .map((value) => value?.trim())
        .filter((value): value is string => Boolean(value)),
    ),
  );
};

const getValueByPath = (values: Record<string, unknown>, path?: string): unknown => {
  if (!path) return undefined;
  return path.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') return undefined;
    return (current as Record<string, unknown>)[segment];
  }, values);
};

const equals = (left: unknown, right: unknown) => Object.is(left, right);

const matchVisibilityCondition = (
  condition: DynamicFormVisibilityCondition,
  values: Record<string, unknown>,
) => {
  const actual = getValueByPath(values, condition.field);
  const operator = normalizeOperator(condition.operator, condition);
  const candidates = condition.values || (Array.isArray(condition.value) ? condition.value : []);

  switch (operator) {
    case 'NOT_EQUALS':
      return !equals(actual, condition.value);
    case 'IN':
      return candidates.some((candidate) => equals(actual, candidate));
    case 'NOT_IN':
      return !candidates.some((candidate) => equals(actual, candidate));
    case 'TRUTHY':
      return Boolean(actual);
    case 'FALSY':
      return !actual;
    case 'EQUALS':
    default:
      return equals(actual, condition.value);
  }
};

/**
 * 判断动态字段当前是否可见。未配置 visibleWhen 时始终显示；多条件使用 AND 语义。
 */
export const isDynamicFieldVisible = (
  field: DynamicFormField,
  values: Record<string, unknown>,
) => {
  const conditions = normalizeVisibilityConditions(field);
  if (conditions.length === 0) return true;
  return conditions.every(
    (condition) => Boolean(condition.field) && matchVisibilityCondition(condition, values),
  );
};

/**
 * 兼容旧插件历史约定：driverLocation 曾经以普通 INPUT 字段下发，
 * 新版统一提升为 DRIVER 标准组件。插件升级后应直接声明 type=DRIVER。
 */
const normalizeFormField = (field: DynamicFormField): DynamicFormField => {
  const normalized: DynamicFormField = {
    ...field,
    dependsOn: getFieldDependencies(field),
    visibleWhen: normalizeVisibilityConditions(field),
  };
  if (field.key === 'driverLocation' && field.type !== 'DRIVER') {
    normalized.type = 'DRIVER';
  }
  return normalized;
};

/**
 * 将新版 sections 和旧版 formFields 统一归一成分区结构。
 *
 * 新插件优先使用 sections；旧插件无需迁移，扁平 formFields 会自动落到
 * 一个始终展开的“连接参数”分区中。
 */
export const normalizeFormSections = (
  schema?: Pick<DynamicFormSchemaResponse, 'sections' | 'formFields'>,
): DynamicFormSection[] => {
  const sections = (schema?.sections || [])
    .filter((section) => Array.isArray(section?.fields) && section.fields.length > 0)
    .map((section, index) => ({
      ...section,
      key: section.key?.trim() || `section-${index + 1}`,
      title: section.title?.trim() || '连接参数',
      collapsible: section.collapsible === true,
      defaultExpanded: section.defaultExpanded !== false,
      fields: section.fields.map(normalizeFormField),
    }));

  if (sections.length > 0) return sections;

  const legacyFields = schema?.formFields || [];
  if (legacyFields.length === 0) return [];

  return [
    {
      key: 'connection',
      title: '连接参数',
      collapsible: false,
      defaultExpanded: true,
      fields: legacyFields.map(normalizeFormField),
    },
  ];
};

export const flattenFormSectionFields = (
  sections: DynamicFormSection[],
): DynamicFormField[] => sections.flatMap((section) => section.fields || []);

/** 只给当前为空的字段补默认值。 */
export const patchEmptyWithDefaults = (
  current: Record<string, unknown>,
  defaults: Record<string, unknown>,
) => {
  const patch: Record<string, unknown> = {};
  Object.keys(defaults).forEach((key) => {
    const value = current?.[key];
    if (value === undefined || value === null || value === '') {
      patch[key] = defaults[key];
    }
  });
  return patch;
};
