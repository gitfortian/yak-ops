import { render, screen } from '@testing-library/react';

import YakStatusIcon, { type YakStatus, YAK_STATUS_VALUES } from './index';

describe('YakStatusIcon', () => {
  it.each(YAK_STATUS_VALUES)('renders the %s semantic status', (status) => {
    const { container } = render(<YakStatusIcon status={status} />);
    const icon = container.querySelector('svg');

    expect(icon).toHaveClass(
      'yak-status-icon',
      `yak-status-icon--${status}`,
      'yak-status-icon--animated',
    );
    expect(icon).toHaveAttribute('data-status', status);
    expect(icon).toHaveAttribute('width', '18');
    expect(icon).toHaveAttribute('height', '18');
    expect(icon).toHaveAttribute('aria-hidden', 'true');
  });

  it('supports size, className and disabling motion', () => {
    const { container } = render(
      <YakStatusIcon
        status="running"
        size={24}
        animated={false}
        className="workflow-status-icon"
      />,
    );
    const icon = container.querySelector('svg');

    expect(icon).toHaveClass('workflow-status-icon');
    expect(icon).not.toHaveClass('yak-status-icon--animated');
    expect(icon).toHaveAttribute('data-animated', 'false');
    expect(icon).toHaveAttribute('width', '24');
    expect(icon).toHaveAttribute('height', '24');
  });

  it('exposes an accessible name when title is provided', () => {
    render(<YakStatusIcon status="failed" title="任务执行失败" />);

    expect(screen.getByRole('img', { name: '任务执行失败' })).toBeInTheDocument();
  });

  it('keeps the public status type business-agnostic', () => {
    const status: YakStatus = 'success';

    expect(status).toBe('success');
    expect(YAK_STATUS_VALUES).not.toContain('COMPLETED');
    expect(YAK_STATUS_VALUES).not.toContain('SYNC_SUCCESS');
  });
});
