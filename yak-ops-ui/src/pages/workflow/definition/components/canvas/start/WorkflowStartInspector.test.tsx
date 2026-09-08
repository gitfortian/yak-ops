import { render, screen } from '@testing-library/react';

import WorkflowStartInspector from './WorkflowStartInspector';

jest.mock('@/services/workflow', () => ({
  getWorkflowInstances: jest.fn().mockResolvedValue([]),
}));

jest.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) => id,
  }),
}));

jest.mock('@/components/YakTab', () => ({
  __esModule: true,
  default: () => <div data-testid="start-inspector-tabs" />,
}));

jest.mock('../WorkflowNextStep', () => ({
  __esModule: true,
  default: () => <div data-testid="workflow-next-step" />,
}));

jest.mock('../useWorkflowInspectorBehavior', () => ({
  __esModule: true,
  default: () => ({
    panelWidth: 380,
    resizing: false,
    handleResizePointerDown: jest.fn(),
  }),
}));

describe('WorkflowStartInspector', () => {
  it('renders a newly created empty workflow without requiring persisted system variables', () => {
    render(
      <WorkflowStartInspector
        definitionId="workflow-1"
        workflowName="New workflow"
        config={{
          position: { x: 80, y: 160 },
          inputs: [],
          variables: [],
          nextNodeIds: [],
        }}
        locked={false}
        nextNodes={[]}
        appendOptions={[]}
        onChange={jest.fn()}
        onAppend={jest.fn()}
        onClose={jest.fn()}
      />,
    );

    expect(screen.getByText('sys.definitionId')).toBeTruthy();
    expect(screen.getByText('sys.workflowName')).toBeTruthy();
    expect(screen.getByText('workflow-1 · STRING')).toBeTruthy();
    expect(screen.getByText('New workflow · STRING')).toBeTruthy();
  });
});
