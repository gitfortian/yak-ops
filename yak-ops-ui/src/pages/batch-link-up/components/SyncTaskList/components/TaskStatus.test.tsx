import { render, screen } from '@testing-library/react';

import TaskStatus from './TaskStatus';

describe('offline sync TaskStatus', () => {
  it.each([
    ['CREATED', 'pending', '已创建', 'false'],
    ['SUBMITTED', 'pending', '提交中', 'true'],
    ['QUEUED', 'pending', '排队中', 'true'],
    ['RUNNING', 'running', '运行中', 'true'],
    ['SUCCEEDED', 'success', '已完成', 'false'],
    ['FAILED', 'failed', '失败', 'false'],
    ['PAUSED', 'paused', '已暂停', 'false'],
    ['CANCELED', 'canceled', '已取消', 'false'],
    ['LOST', 'warning', '状态丢失', 'false'],
  ])(
    'maps %s to the %s Yak status',
    (businessStatus, yakStatus, label, animated) => {
      const { container } = render(<TaskStatus status={businessStatus} />);

      expect(screen.getByText(label)).toBeInTheDocument();
      expect(container.querySelector('svg')).toHaveAttribute(
        'data-status',
        yakStatus,
      );
      expect(container.querySelector('svg')).toHaveAttribute(
        'data-animated',
        animated,
      );
    },
  );

  it.each([
    ['COMPLETED', 'success', '已完成'],
    ['SUCCESS', 'success', '已完成'],
    ['WAITING', 'pending', '排队中'],
    ['CANCELLED', 'canceled', '已取消'],
    ['STOPPED', 'canceled', '已取消'],
    ['NOT_STARTED', 'pending', '未运行'],
  ])('normalizes legacy status %s', (businessStatus, yakStatus, label) => {
    const { container } = render(<TaskStatus status={businessStatus} />);

    expect(screen.getByText(label)).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveAttribute(
      'data-status',
      yakStatus,
    );
  });

  it('uses a static pending state when the task has not run yet', () => {
    const { container } = render(<TaskStatus />);

    expect(screen.getByText('未运行')).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveAttribute(
      'data-status',
      'pending',
    );
    expect(container.querySelector('svg')).toHaveAttribute(
      'data-animated',
      'false',
    );
  });

  it('falls back to the unknown Yak status without hiding the backend value', () => {
    const { container } = render(<TaskStatus status="CUSTOM_STATE" />);

    expect(screen.getByText('CUSTOM_STATE')).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveAttribute(
      'data-status',
      'unknown',
    );
  });
});
