import type { Rule } from 'antd/es/form';

import type {
  DynamicFormField,
  DynamicFormFieldRule,
  DynamicFormSchemaResponse,
  DynamicFormSection,
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
    initialValues[field.key] = parseDefaultValueByType(field);
  });
  return initialValues;
};

const parseDefaultValueByType = (field: DynamicFormField) => {
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
      fields: [...section.fields],
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
      fields: [...legacyFields],
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
