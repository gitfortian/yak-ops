import { YakButton } from '@/components/ui';
import type { RuntimeCapabilities } from '@/services/realtime-sync';
import { PlusOutlined } from '@ant-design/icons';
import { Descriptions, Popover } from 'antd';

interface RealtimeSyncPageHeaderProps {
  capabilities: RuntimeCapabilities;
  streamConnected: boolean;
  onCreate: () => void;
}

const RealtimeSyncPageHeader = ({
  capabilities,
  streamConnected,
  onCreate,
}: RealtimeSyncPageHeaderProps) => (
  <header className="flex items-start justify-between gap-6">
    <div className="min-w-0">
      <h1 className="m-0 text-[17px] font-semibold leading-7 text-[#101828]">
        实时同步
      </h1>

      <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px] leading-5 text-[#98a2b3]">
        <span>管理实时同步任务、发布状态与 Flink 运行实例</span>

        <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
          <span
            className={[
              'h-1.5 w-1.5 rounded-full',
              streamConnected ? 'bg-[#12b76a]' : 'bg-[#d0d5dd]',
            ].join(' ')}
          />
          {streamConnected ? '事件通道已连接' : '轮询更新'}
        </span>

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
          <button
            type="button"
            className="border-0 bg-transparent p-0 text-[12px] font-medium leading-5 text-[#667085] transition-colors hover:text-[#344054]"
          >
            默认环境 · {capabilities.runtimeEnvironmentName || '未配置'}
          </button>
        </Popover>
      </div>
    </div>

    <YakButton
      type="primary"
      size="small"
      icon={<PlusOutlined />}
      className="!h-9 !shrink-0 !rounded-[9px] !px-4"
      onClick={onCreate}
    >
      新建同步任务
    </YakButton>
  </header>
);

export default RealtimeSyncPageHeader;
