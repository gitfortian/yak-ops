import type { PublishedDataset } from '@/services/dataset';
import { listPublishedDatasets } from '@/services/dataset';
import {
  getDigitalScreen,
  publishDigitalScreen,
  unpublishDigitalScreen,
  updateDigitalScreen,
  type DigitalScreenBindings,
  type DigitalScreenComponentBinding,
  type DigitalScreenInstance,
} from '@/services/digital-screen';
import { resolveScreenTemplateById } from '@/services/screen-template-service';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useScreenRuntimeData } from '../../runtime/hooks/useScreenRuntimeData';

export function useDigitalScreenEditor(id?: string) {
  const [screen, setScreen] = useState<DigitalScreenInstance>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [bindings, setBindings] = useState<DigitalScreenBindings>({});
  const [selectedComponentId, setSelectedComponentId] = useState<string>();
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [isDatasetsLoading, setIsDatasetsLoading] = useState(true);
  const [datasetsError, setDatasetsError] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);

  const template = useMemo(
    () => (screen ? resolveScreenTemplateById(screen.templateId) : undefined),
    [screen],
  );
  const selectedComponent = useMemo(
    () => template?.components.find((component) => component.id === selectedComponentId),
    [template, selectedComponentId],
  );
  const runtime = useScreenRuntimeData(template, bindings, datasets);

  const loadScreen = useCallback(async () => {
    if (!id) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    try {
      const detail = await getDigitalScreen(id);
      setScreen(detail);
      setName(detail.name);
      setDescription(detail.description || '');
      setBindings(detail.bindings || {});
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载数字化大屏失败');
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadScreen();
  }, [loadScreen]);

  useEffect(() => {
    let active = true;
    setIsDatasetsLoading(true);
    setDatasetsError('');
    void listPublishedDatasets()
      .then((values) => {
        if (active) setDatasets(values);
      })
      .catch((error) => {
        if (!active) return;
        setDatasets([]);
        setDatasetsError(error instanceof Error ? error.message : '加载 Dataset 失败');
      })
      .finally(() => {
        if (active) setIsDatasetsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!template) return;
    if (selectedComponentId && template.components.some((component) => component.id === selectedComponentId)) return;
    const preferred = template.components.find((component) => component.type !== 'text')
      || template.components[0];
    setSelectedComponentId(preferred?.id);
  }, [template, selectedComponentId]);

  const saveScreen = async (showSuccessMessage = true) => {
    if (!id) return undefined;
    if (!name.trim()) {
      message.warning('请输入大屏名称');
      return undefined;
    }

    setIsSaving(true);
    try {
      const updated = await updateDigitalScreen(id, { name, description, bindings });
      setScreen(updated);
      setBindings(updated.bindings);
      if (showSuccessMessage) message.success('大屏已保存');
      return updated;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存数字化大屏失败');
      return undefined;
    } finally {
      setIsSaving(false);
    }
  };

  const togglePublish = async () => {
    if (!id || !screen) return;
    setIsPublishing(true);
    try {
      const saved = await saveScreen(false);
      if (!saved) return;
      const updated = saved.status === 'published'
        ? await unpublishDigitalScreen(id)
        : await publishDigitalScreen(id);
      setScreen(updated);
      message.success(updated.status === 'published' ? '大屏已发布' : '大屏已取消发布');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新发布状态失败');
    } finally {
      setIsPublishing(false);
    }
  };

  const updateSelectedBinding = (next?: DigitalScreenComponentBinding) => {
    if (!selectedComponent) return;
    setBindings((current) => {
      const result = { ...current };
      if (next) result[selectedComponent.id] = next;
      else delete result[selectedComponent.id];
      return result;
    });
  };

  const bindableCount = template?.components.filter((component) => component.type !== 'text').length ?? 0;
  const isSelectedQuerying = selectedComponent
    ? runtime.loadingIds.includes(selectedComponent.id)
    : false;
  const selectedQueryError = selectedComponent
    ? runtime.errors[selectedComponent.id]
    : undefined;

  return {
    screen,
    name,
    description,
    bindings,
    selectedComponentId,
    datasets,
    datasetsError,
    isDatasetsLoading,
    isLoading,
    isSaving,
    isPublishing,
    template,
    selectedComponent,
    runtime,
    bindableCount,
    isSelectedQuerying,
    selectedQueryError,
    setName,
    setDescription,
    setSelectedComponentId,
    saveScreen,
    togglePublish,
    updateSelectedBinding,
  };
}
