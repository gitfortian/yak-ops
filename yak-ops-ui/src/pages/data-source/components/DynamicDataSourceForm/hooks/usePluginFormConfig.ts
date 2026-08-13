import { API_SUCCESS_CODE } from '@/services/http/response';
import type { FormInstance } from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useRef,
} from 'react';

import {
  fetchDataSourcePluginConfig,
  installDataSourcePlugin,
} from '../../../service';
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

/**
 * 数据源插件配置统一生命周期。
 *
 * 主编辑器与旧入口共享同一套加载、安装、重试、竞态保护和 Schema 归一化逻辑，
 * 避免把 loading / needInstall / installing / loadError 等布尔状态散落在页面组件中。
 */
export function usePluginFormConfig(params: {
  dbType: string;
  configForm: FormInstance;
  initialConfig?: Record<string, unknown>;
  /** 主编辑器切换数据源类型时需要清空旧配置；旧入口默认保持原行为。 */
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
  const requestSeqRef = useRef(0);

  const loadFormConfig = useCallback(async () => {
    if (!dbType) {
      requestSeqRef.current += 1;
      dispatch({ type: 'RESET' });
      if (resetOnLoad) configForm.resetFields();
      return false;
    }

    const requestSeq = requestSeqRef.current + 1;
    requestSeqRef.current = requestSeq;
    dispatch({ type: 'LOAD_START' });
    if (resetOnLoad) configForm.resetFields();

    try {
      const response = await fetchDataSourcePluginConfig(dbType);
      if (requestSeq !== requestSeqRef.current) return false;

      if (response?.code !== API_SUCCESS_CODE) {
        dispatch({
          type: 'LOAD_FAILED',
          message:
            response?.msg ||
            response?.message ||
            '数据源插件配置加载失败，请稍后重试',
        });
        return false;
      }

      const data = response.data || { formFields: [] };
      if (data.installRequired) {
        dispatch({
          type: 'INSTALL_REQUIRED',
          message: data.installHint || '当前数据源插件尚未安装',
        });
        return false;
      }

      const sections = normalizeFormSections(data);
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
      if (requestSeq !== requestSeqRef.current) return false;
      dispatch({
        type: 'LOAD_FAILED',
        message: errorMessage(error, '数据源插件配置加载失败，请稍后重试'),
      });
      return false;
    }
  }, [configForm, dbType, initialConfig, resetOnLoad]);

  const installPlugin = useCallback(async () => {
    if (!dbType || state.status === PLUGIN_CONFIG_STATUS.INSTALLING) {
      return false;
    }

    // 使仍在进行中的加载请求失效；切换数据源类型或卸载组件时也会推进该序号。
    const requestSeq = requestSeqRef.current + 1;
    requestSeqRef.current = requestSeq;
    dispatch({ type: 'INSTALL_START' });

    try {
      const response = await installDataSourcePlugin(dbType);
      if (requestSeq !== requestSeqRef.current) return false;

      if (response?.code !== API_SUCCESS_CODE) {
        dispatch({
          type: 'INSTALL_FAILED',
          message:
            response?.msg || response?.message || '数据源插件安装失败，请重试',
        });
        return false;
      }

      await loadFormConfig();
      return true;
    } catch (error) {
      if (requestSeq !== requestSeqRef.current) return false;
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
      requestSeqRef.current += 1;
    };
  }, [loadFormConfig]);

  const formConfig = useMemo<DynamicFormField[]>(
    () => flattenFormSectionFields(state.sections),
    [state.sections],
  );

  return {
    // 保留旧 Hook 返回值，避免影响仍在复用的入口。
    formConfig,
    loading: state.status === PLUGIN_CONFIG_STATUS.LOADING,
    // 新版状态模型。
    formSections: state.sections,
    status: state.status,
    message: state.message,
    installing: state.status === PLUGIN_CONFIG_STATUS.INSTALLING,
    reload: loadFormConfig,
    installPlugin,
  };
}
