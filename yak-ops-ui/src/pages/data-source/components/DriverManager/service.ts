import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

export interface DriverUploadResult {
  fileName?: string;
  path?: string;
}

const DRIVER_UPLOAD_API = '/api/v1/data-source/plugin/driver/upload';

export async function uploadDataSourceDriver(
  pluginType: string,
  file: File,
): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('pluginType', pluginType);

  const response = await HttpUtils.postForm<DriverUploadResult | string>(
    DRIVER_UPLOAD_API,
    formData,
  );

  if (response?.code !== API_SUCCESS_CODE) {
    throw new Error(response?.message || response?.msg || '驱动包上传失败');
  }

  const data = response?.data;
  const driverLocation =
    typeof data === 'string' ? data : data?.path || data?.fileName || '';

  if (!driverLocation) {
    throw new Error('驱动包上传成功，但服务端未返回驱动位置');
  }

  return driverLocation;
}
