import { YakButton } from '@/components/ui';
import type { RuntimeCapabilities } from '@/services/realtime-sync';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Descriptions, Popover, Tooltip } from 'antd';

interface RealtimeSyncCapabilityBarProps {
  capabilities: RuntimeCapabilities;
  loading: boolean;
  onRefresh: () => void;
  onCreate: () => void;
}

const RealtimeSyncCapabilityBar = ({
  capabilities,
  loading,
  onRefresh,
  onCreate,
}: RealtimeSyncCapabilityBarProps) => (
  <div className="flex min-h-[48px] items-center justify-between">
    <div className="flex items-center gap-2">
      <Popover
        placement="bottomLeft"
        content={
          <Descriptions size="small" column={1} className="w-[420px]">
            <Descriptions.Item label="默认运行环境">
              {capabilities.runtimeEnvironmentName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="提交引擎">
              {capabilities.runtimeVersion || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="Flink">
              {capabilities.flinkVersion || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="Flink CDC">
              {capabilities.flinkCdcVersion || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="REST">
              {capabilities.restUrl || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="语义">
              {capabilities.deliverySemantics || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="说明">
              这里只展示默认环境能力；任务启动会按各自绑定的运行环境独立校验。
            </Descriptions.Item>
          </Descriptions>
        }
      >
        <YakButton size="small" className="!h-8">
          默认环境能力
        </YakButton>
      </Popover>

      <Tooltip title="刷新任务、数据源与运行环境">
        <YakButton
          size="small"
          iconOnly
          icon={<ReloadOutlined spin={loading} />}
          className="!h-8 !w-8 !px-0"
          onClick={onRefresh}
        />
      </Tooltip>
    </div>

    <YakButton
      type="primary"
      danger
      size="small"
      icon={<PlusOutlined />}
      className="!h-8"
      onClick={onCreate}
    >
      新建同步任务
    </YakButton>
  </div>
);

export default RealtimeSyncCapabilityBar;
