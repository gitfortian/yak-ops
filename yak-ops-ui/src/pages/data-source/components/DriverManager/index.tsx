import { YakButton } from '@/components/ui';
import { uploadDataSourceDriver } from '@/services/data-source';
import { UploadOutlined } from '@ant-design/icons';
import { Input, message, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { useMemo, useState } from 'react';

const DEFAULT_MAX_SIZE_MB = 200;

export interface DriverManagerProps {
  dbType: string;
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  maxSizeMB?: number;
}

const DriverManager = ({
  dbType,
  value,
  onChange,
  placeholder = '请输入驱动包路径，或上传 .jar 文件',
  disabled = false,
  maxSizeMB = DEFAULT_MAX_SIZE_MB,
}: DriverManagerProps) => {
  const [uploading, setUploading] = useState(false);

  const uploadProps = useMemo<UploadProps>(
    () => ({
      accept: '.jar,application/java-archive',
      multiple: false,
      showUploadList: false,
      disabled: disabled || uploading,
      beforeUpload: (file) => {
        if (!file.name.toLowerCase().endsWith('.jar')) {
          message.error('只允许上传 .jar 驱动包');
          return Upload.LIST_IGNORE;
        }

        if (file.size / 1024 / 1024 > maxSizeMB) {
          message.error(`驱动包不能超过 ${maxSizeMB}MB`);
          return Upload.LIST_IGNORE;
        }

        return true;
      },
      customRequest: async ({ file, onSuccess, onError }) => {
        try {
          setUploading(true);
          const driverLocation = await uploadDataSourceDriver(
            dbType,
            file as File,
          );
          onChange?.(driverLocation);
          message.success('驱动包上传成功');
          onSuccess?.({ driverLocation });
        } catch (error) {
          const uploadError =
            error instanceof Error ? error : new Error('驱动包上传失败');
          message.error(uploadError.message);
          onError?.(uploadError);
        } finally {
          setUploading(false);
        }
      },
    }),
    [dbType, disabled, maxSizeMB, onChange, uploading],
  );

  return (
    <div className="w-full">
      <div className="flex w-full items-center gap-2">
        <Input
          variant="filled"
          value={value}
          disabled={disabled}
          allowClear
          placeholder={placeholder}
          onChange={(event) => onChange?.(event.target.value)}
        />

        <Upload {...uploadProps}>
          <YakButton
            className="shrink-0"
            icon={<UploadOutlined />}
            loading={uploading}
            disabled={disabled}
          >
            上传驱动
          </YakButton>
        </Upload>
      </div>

      <div className="mt-1.5 text-[11px] leading-4 text-[#98a2b3]">
        支持 JAR 驱动包，单文件不超过 {maxSizeMB}MB；也可以直接填写已部署的驱动路径。
      </div>
    </div>
  );
};

export default DriverManager;
