import { resolveScreenTemplateById } from '@/services/screen-template-service';
import HttpUtils from '@/utils/HttpUtils';
import type {
  CreateDigitalScreenInput,
  DigitalScreenBindings,
  DigitalScreenInstance,
  DigitalScreenStatus,
  UpdateDigitalScreenInput,
} from '../types';
import type { ScreenRepository } from './screen-repository';

const DIGITAL_SCREEN_API = '/api/v1/digital-screens';

interface DigitalScreenWire {
  id: string;
  name: string;
  description?: string | null;
  templateId: string;
  templateVersion: 1;
  status: DigitalScreenStatus;
  bindings?: DigitalScreenBindings | null;
  publishedTime?: string | null;
  createTime: string;
  updateTime: string;
}

const toInstance = (wire: DigitalScreenWire): DigitalScreenInstance => ({
  id: String(wire.id),
  name: wire.name,
  description: wire.description || undefined,
  templateId: wire.templateId,
  templateVersion: wire.templateVersion,
  status: wire.status,
  bindings: wire.bindings ?? {},
  publishedAt: wire.publishedTime || undefined,
  createdAt: wire.createTime,
  updatedAt: wire.updateTime,
});

class HttpScreenRepository implements ScreenRepository {
  async list() {
    const values = await HttpUtils.getData<DigitalScreenWire[]>(DIGITAL_SCREEN_API);
    return (values || []).map(toInstance);
  }

  async get(id: string) {
    return toInstance(await HttpUtils.getData<DigitalScreenWire>(`${DIGITAL_SCREEN_API}/${id}`));
  }

  async create(input: CreateDigitalScreenInput) {
    const name = input.name.trim();
    if (!name) throw new Error('请输入大屏名称');
    if (!resolveScreenTemplateById(input.templateId)) throw new Error('所选大屏模板不存在');

    return toInstance(await HttpUtils.postData<DigitalScreenWire>(DIGITAL_SCREEN_API, {
      name,
      description: input.description?.trim() || undefined,
      templateId: input.templateId,
      bindings: input.bindings ?? {},
    }));
  }

  async update(id: string, input: UpdateDigitalScreenInput) {
    const payload: Record<string, unknown> = {};
    if (input.name !== undefined) {
      const name = input.name.trim();
      if (!name) throw new Error('请输入大屏名称');
      payload.name = name;
    }
    if (input.description !== undefined) payload.description = input.description.trim();
    if (input.bindings !== undefined) payload.bindings = input.bindings;

    return toInstance(await HttpUtils.putData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}`,
      payload,
    ));
  }

  async publish(id: string) {
    return toInstance(await HttpUtils.postData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}/publish`,
    ));
  }

  async unpublish(id: string) {
    return toInstance(await HttpUtils.postData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}/offline`,
    ));
  }

  async duplicate(id: string) {
    return toInstance(await HttpUtils.postData<DigitalScreenWire>(
      `${DIGITAL_SCREEN_API}/${id}/duplicate`,
    ));
  }

  async remove(id: string) {
    await HttpUtils.deleteData<boolean>(`${DIGITAL_SCREEN_API}/${id}`);
  }
}

export const httpScreenRepository: ScreenRepository = new HttpScreenRepository();
