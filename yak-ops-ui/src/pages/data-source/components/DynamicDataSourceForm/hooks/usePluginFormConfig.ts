import {
  getDataSourcePluginConfig,
  installDataSourcePlugin,
} from '@/services/data-source';
import type { FormInstance } from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useRef,
} from 'react';

import type { DynamicFormField } from '../../../types';
import {
  flattenFormSectionFields,
  getConfigInitialValues,
  normalizeConfigValuesForForm,
  normalizeFormSections,
  patchEmptyWithDefaults,
} from '../utils/formUtils';
import {
  INITIAL_PLUGIN_CONFIG_STATE,
  PLUGIN_CONFIG_STATUS,
  pluginConfigStateReducer,
} from './pluginConfigState';

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

/** 数据源插件配置统一生命周期。 */
export function usePluginFormConfig(params: {
  dbType: string;
  configForm: FormInstance;
  initialConfig?: Record<string, unknown>;
  /** 主编辑器切换数据源类型时需要清空旧配置。 */
  resetOnLoad?: boolean;
  intl?: unknown;
}) {
  const {
    dbType,
    configForm,
    initialConfig,
    resetOnLoad = false,
  } = params;
  const [state, dispatch] = useReducer(
    pluginConfigStateReducer,
    INITIAL_PLUGIN_CONFIG_STATE,
  );
  const requestSequenceRef = useRef(0);

  const loadFormConfig = useCallback(async () => {
    if (!dbType) {
      requestSequenceRef.current += 1;
      dispatch({ type: 'RESET' });
      if (resetOnLoad) configForm.resetFields();
      return false;
    }

    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    dispatch({ type: 'LOAD_START' });
    if (resetOnLoad) configForm.resetFields();

    try {
      const data = await getDataSourcePluginConfig(dbType);
      if (requestSequence !== requestSequenceRef.current) return false;

      if (data.installRequired) {
        dispatch({
          type: 'INSTALL_REQUIRED',
          message: data.installHint || '当前数据源插件尚未安装',
        });
        return false;
      }

      const sections = normalizeFormSections(data || { formFields: [] });
      const fields = flattenFormSectionFields(sections);
      const defaults = getConfigInitialValues(fields);

      if (resetOnLoad) {
        configForm.setFieldsValue({
          ...defaults,
          ...normalizeConfigValuesForForm(fields, initialConfig),
        });
      } else {
        const current = normalizeConfigValuesForForm(
          fields,
          configForm.getFieldsValue(true),
        );
        const patch = patchEmptyWithDefaults(current, defaults);
        configForm.setFieldsValue({ ...current, ...patch });
      }

      dispatch({ type: 'LOAD_SUCCESS', sections });
      return true;
    } catch (error) {
      if (requestSequence !== requestSequenceRef.current) return false;
      dispatch({
        type: 'LOAD_FAILED',
        message: errorMessage(
          error,
          '数据源插件配置加载失败，请稍后重试',
        ),
      });
      return false;
    }
  }, [configForm, dbType, initialConfig, resetOnLoad]);

  const installPlugin = useCallback(async () => {
    if (!dbType || state.status === PLUGIN_CONFIG_STATUS.INSTALLING) {
      return false;
    }

    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    dispatch({ type: 'INSTALL_START' });

    try {
      await installDataSourcePlugin(dbType);
      if (requestSequence !== requestSequenceRef.current) return false;

      await loadFormConfig();
      return true;
    } catch (error) {
      if (requestSequence !== requestSequenceRef.current) return false;
      dispatch({
        type: 'INSTALL_FAILED',
        message: errorMessage(error, '数据源插件安装失败，请重试'),
      });
      return false;
    }
  }, [dbType, loadFormConfig, state.status]);

  useEffect(() => {
    void loadFormConfig();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadFormConfig]);

  const formConfig = useMemo<DynamicFormField[]>(
    () => flattenFormSectionFields(state.sections),
    [state.sections],
  );

  return {
    formConfig,
    loading: state.status === PLUGIN_CONFIG_STATUS.LOADING,
    formSections: state.sections,
    status: state.status,
    message: state.message,
    installing: state.status === PLUGIN_CONFIG_STATUS.INSTALLING,
    reload: loadFormConfig,
    installPlugin,
  };
}
