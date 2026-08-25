import YakButton from '@/components/YakButton';
import { PlusOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import React from 'react';
import './index.less';

type EmptyStateProps = {
  onCreate?: () => void;
};

const EmptyState: React.FC<EmptyStateProps> = ({ onCreate }) => {
  const intl = useIntl();

  return (
    <div className="datasource-empty-state">
      <div className="datasource-empty-state__bg">
        <span className="bubble bubble-1" />
        <span className="bubble bubble-2" />
        <span className="bubble bubble-3" />
      </div>

      <div className="datasource-empty-state__emoji-wrap">
        <div className="datasource-empty-state__emoji">🥹</div>
        <div className="datasource-empty-state__shadow" />
      </div>

      <div className="datasource-empty-state__title">
        {intl.formatMessage({
          id: 'pages.datasource.empty',
          defaultMessage: '我翻了一圈，这里还是空空的 🫥',
        })}
      </div>

      <div className="datasource-empty-state__desc">
        {intl.formatMessage({
          id: 'pages.datasource.empty.desc',
          defaultMessage: '先创建一个数据源吧，这样我们就可以开始配置和使用了。',
        })}
      </div>

      <div className="datasource-empty-state__tip">
        {intl.formatMessage({
          id: 'pages.datasource.empty.tip',
          defaultMessage: '点一下“创建数据源”，马上就能热闹起来 ✨',
        })}
      </div>

      <div className="datasource-empty-state__actions">
        <YakButton
          block
          type="primary"
          icon={<PlusOutlined />}
          onClick={onCreate}
        >
          {intl.formatMessage({
            id: 'pages.datasource.create',
            defaultMessage: '创建数据源',
          })}
        </YakButton>
      </div>
    </div>
  );
};

export default EmptyState;
