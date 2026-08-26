import { YakButton } from '@/components/ui';
import { Boxes, RefreshCw } from 'lucide-react';
import {
  Component,
  type ReactNode,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import { isDevelopmentTaskNode } from '../node-model';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNodeType,
  DevelopmentResourceNode,
} from '../types';
import DevelopmentWelcome from './DevelopmentWelcome';
import DevelopmentWorkbench from './workbench/DevelopmentWorkbench';

interface DevelopmentEditorWorkspaceProps {
  nodes: DevelopmentResourceNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeFocus: (nodeId?: DevelopmentId) => void;
  onCreateNode: (type: DevelopmentNodeType) => void;
  onNodesChanged?: () => void | Promise<void>;
}

class ResourceEditorBoundary extends Component<
  { resourceKey: DevelopmentId; children: ReactNode },
  { error?: string }
> {
  state = {};

  static getDerivedStateFromError(error: unknown) {
    return {
      error: error instanceof Error ? error.message : '资源编辑器发生未知错误',
    };
  }

  componentDidUpdate(previous: { resourceKey: DevelopmentId }) {
    if (previous.resourceKey !== this.props.resourceKey && this.state.error) {
      this.setState({ error: undefined });
    }
  }

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <div className="flex flex-1 items-center justify-center bg-white">
        <div className="text-center">
          <Boxes className="mx-auto text-[#98a2b3]" />
          <div className="mt-3 text-sm">资源编辑器加载异常</div>
          <div className="mt-2 text-xs text-[#98a2b3]">
            {this.state.error}
          </div>
          <YakButton
            className="mt-4"
            size="small"
            icon={<RefreshCw size={13} />}
            onClick={() => this.setState({ error: undefined })}
          >
            重新渲染
          </YakButton>
        </div>
      </div>
    );
  }
}

export default function DevelopmentEditorWorkspace({
  nodes,
  directories,
  selectedNodeId,
  onNodeFocus,
  onCreateNode,
  onNodesChanged,
}: DevelopmentEditorWorkspaceProps) {
  const [focusedNodeId, setFocusedNodeId] = useState<
    DevelopmentId | undefined
  >(selectedNodeId);
  const requestedNodeId = useMemo(() => {
    if (typeof window === 'undefined') return undefined;
    return (
      new URLSearchParams(window.location.search).get('nodeId')?.trim() ||
      undefined
    );
  }, []);
  const deepLinkAppliedRef = useRef(false);

  useEffect(() => {
    if (selectedNodeId && nodes.some((node) => node.id === selectedNodeId)) {
      setFocusedNodeId(selectedNodeId);
      return;
    }

    if (
      !deepLinkAppliedRef.current &&
      requestedNodeId &&
      nodes.some((node) => node.id === requestedNodeId)
    ) {
      deepLinkAppliedRef.current = true;
      setFocusedNodeId(requestedNodeId);
      onNodeFocus(requestedNodeId);
    }
  }, [nodes, onNodeFocus, requestedNodeId, selectedNodeId]);

  const effectiveNodeId =
    selectedNodeId && nodes.some((node) => node.id === selectedNodeId)
      ? selectedNodeId
      : focusedNodeId;
  const selectedResource = useMemo(
    () => nodes.find((node) => node.id === effectiveNodeId),
    [effectiveNodeId, nodes],
  );
  const workbenchNodes = useMemo(
    () =>
      nodes.filter(
        (node) =>
          isDevelopmentTaskNode(node) ||
          node.type === 'DATA_SERVICE' ||
          node.type === 'DATASET',
      ),
    [nodes],
  );

  const handleNodeFocus = (nodeId?: DevelopmentId) => {
    setFocusedNodeId(nodeId);
    onNodeFocus(nodeId);
  };

  if (!selectedResource) {
    return <DevelopmentWelcome onCreateNode={onCreateNode} />;
  }

  if (
    isDevelopmentTaskNode(selectedResource) ||
    selectedResource.type === 'DATA_SERVICE' ||
    selectedResource.type === 'DATASET'
  ) {
    return (
      <ResourceEditorBoundary resourceKey={selectedResource.id}>
        <DevelopmentWorkbench
          nodes={workbenchNodes}
          directories={directories}
          selectedNodeId={selectedResource.id}
          onNodeFocus={handleNodeFocus}
          onNodesChanged={onNodesChanged}
        />
      </ResourceEditorBoundary>
    );
  }

  return null;
}
