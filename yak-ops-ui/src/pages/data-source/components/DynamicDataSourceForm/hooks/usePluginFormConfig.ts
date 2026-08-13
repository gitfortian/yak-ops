import { API_SUCCESS_CODE } from '@/services/http/response';
import type { FormInstance } from 'antd';
import { useEffect, useState } from 'react';

import { fetchDataSourcePluginConfig } from '../../../service';
import type { DynamicFormField } from '../../../types';
import {
  flattenFormSectionFields,
  getConfigInitialValues,
  normalizeFormSections,
  patchEmptyWithDefaults,
} from '../utils/formUtils';

/**
 * 保留给仍在复用该 Hook 的旧表单入口。
 * 数据源主编辑器已经自行管理 Schema 加载，这里与主流程共享同一 service 和归一化规则。
 */
export function usePluginFormConfig(params: {
  dbType: string;
  configForm: FormInstance;
  intl?: unknown;
}) {
  const { dbType, configForm } = params;
  const [formConfig, setFormConfig] = useState<DynamicFormField[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function run() {
      try {
        setLoading(true);
        const response = await fetchDataSourcePluginConfig(dbType);
        if (cancelled) return;

        if (response?.code !== API_SUCCESS_CODE) {
          setFormConfig([]);
          return;
        }

        const sections = normalizeFormSections(response.data);
        const fields = flattenFormSectionFields(sections);
        setFormConfig(fields);

        const defaults = getConfigInitialValues(fields);
        const current = configForm.getFieldsValue(true);
        const patch = patchEmptyWithDefaults(current, defaults);
        if (Object.keys(patch).length > 0) configForm.setFieldsValue(patch);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    if (dbType) void run();
    return () => {
      cancelled = true;
    };
  }, [configForm, dbType]);

  return { formConfig, loading };
}
