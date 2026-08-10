import type { DataNode } from 'antd/es/tree';
import type { TreeProps } from 'antd';
import { Button, Empty, Spin, Tooltip, Tree } from 'antd';
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Folder,
  FolderOpen,
  FolderPlus,
  Layers3,
  Workflow,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';

export type DevelopmentTreeNodeType =
  | 'all'
  | 'project'
  | 'directory'
  | 'unassigned';

export interface DevelopmentTreeNode extends DataNode {
  key: string;
  title: string;
  nodeType: DevelopmentTreeNodeType;
  projectId?: number;
  directoryId?: number;
  count?: number;
  children?: DevelopmentTreeNode[];
}

interface DevelopmentTreePaneProps {
  treeData: DevelopmentTreeNode[];
  treeLoading: boolean;
  selectedNodeKey?: string;
  leftWidth: number;
  collapsed: boolean;
  createDisabled?: boolean;
  onCreateDirectory: () => void;
  onSelect: TreeProps['onSelect'];
  onResizeStart: (event: ReactPointerEvent) => void;
  onCollapsedChange: (collapsed: boolean) => void;
}

const DevelopmentTreePane = ({
  treeData,
  treeLoading,
  selectedNodeKey,
  leftWidth,
  collapsed,
  createDisabled = false,
  onCreateDirectory,
  onSelect,
  onResizeStart,
  onCollapsedChange,
}: DevelopmentTreePaneProps) => {
  const renderTitle: TreeProps['titleRender'] = (rawNode) => {
    const node = rawNode as DevelopmentTreeNode;
    const isDirectory = node.nodeType === 'directory';
    const isProject = node.nodeType === 'project';
    const isAll = node.nodeType === 'all';

    return (
      <div
        className="flex min-w-0 flex-1 items-center gap-2"
        title={node.title}
      >
        {isProject ? (
          <Workflow
            size={14}
            strokeWidth={2}
            className="shrink-0 text-[#ff7a00]"
          />
        ) : isDirectory ? (
          <Folder
            size={14}
            strokeWidth={1.8}
            className="shrink-0 text-[#98a2b3]"
          />
        ) : isAll ? (
          <Layers3
            size={14}
            strokeWidth={1.8}
            className="shrink-0 text-[#667085]"
          />
        ) : (
          <FolderOpen
            size={14}
            strokeWidth={1.8}
            className="shrink-0 text-[#98a2b3]"
          />
        )}

        <span
          className={[
            'min-w-0 flex-1 truncate text-[13px] leading-8',
            isProject
              ? 'font-medium text-[#1f2937]'
              : 'font-normal text-[#344054]',
          ].join(' ')}
        >
          {node.title}
        </span>

        {typeof node.count === 'number' ? (
          <span className="shrink-0 text-[10px] text-[rgba(22,24,35,.3)]">
            {node.count}
          </span>
        ) : null}
      </div>
    );
  };

  return (
    <>
      <aside
        className={[
          'group relative shrink-0 overflow-hidden bg-white',
          'transition-[width] duration-200 ease-out',
        ].join(' ')}
        style={{ width: collapsed ? 0 : leftWidth }}
      >
        <div
          className="flex h-full flex-col overflow-hidden py-3"
          style={{ width: leftWidth }}
        >
          <div className="flex h-8 items-center justify-between px-[14px]">
            <div className="text-[13px] font-semibold text-[#161823]">
              开发目录
            </div>
            <Tooltip
              title={createDisabled ? '请先选择一个项目' : '新建目录'}
              placement="right"
            >
              <span>
                <Button
                  type="text"
                  size="small"
                  disabled={createDisabled}
                  icon={<FolderPlus size={15} strokeWidth={1.8} />}
                  className="!flex !h-7 !w-7 !items-center !justify-center !p-0"
                  onClick={onCreateDirectory}
                />
              </span>
            </Tooltip>
          </div>

          <div className="mt-1 min-h-0 flex-1 overflow-y-auto px-[14px]">
            <Spin
              spinning={treeLoading}
              wrapperClassName="block min-h-full"
            >
              {treeData.length ? (
                <Tree
                  blockNode
                  defaultExpandAll
                  selectedKeys={
                    selectedNodeKey ? [selectedNodeKey] : []
                  }
                  treeData={treeData}
                  titleRender={renderTitle}
                  switcherIcon={
                    <ChevronDown size={12} strokeWidth={1.8} />
                  }
                  onSelect={onSelect}
                  className="development-tree bg-transparent"
                />
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无开发目录"
                  className="mt-10"
                />
              )}
            </Spin>
          </div>
        </div>
      </aside>

      <div
        role="separator"
        aria-label="调整开发目录面板宽度"
        aria-orientation="vertical"
        onPointerDown={collapsed ? undefined : onResizeStart}
        className={[
          'group relative z-20 w-3 shrink-0 touch-none',
          collapsed ? 'cursor-default' : 'cursor-col-resize',
        ].join(' ')}
      >
        <div
          className={[
            'pointer-events-none absolute inset-y-0 left-1/2',
            'w-px -translate-x-1/2 bg-[#dfe3e8]',
            'transition-[width,background-color] duration-150',
            !collapsed
              ? 'group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:bg-[rgba(254,44,85,1)]'
              : '',
          ].join(' ')}
        />

        <button
          type="button"
          aria-label={collapsed ? '展开开发目录面板' : '收起开发目录面板'}
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onCollapsedChange(!collapsed)}
          className={[
            'absolute left-1/2 top-1/2 z-20',
            'flex h-8 w-4 -translate-x-1/2 -translate-y-1/2',
            'items-center justify-center rounded-[3px]',
            'border border-[#dfe3e8] bg-white text-[#7b808a]',
            'shadow-[0_1px_2px_rgba(16,24,40,0.05)]',
            'transition-[color,border-color,box-shadow] duration-150',
            'hover:border-[#cfd4dc] hover:text-[#344054]',
            'focus:outline-none focus-visible:ring-2',
            'focus-visible:ring-[rgba(254,44,85,.16)]',
          ].join(' ')}
        >
          {collapsed ? (
            <ChevronRight size={12} />
          ) : (
            <ChevronLeft size={12} />
          )}
        </button>
      </div>

      <style>{`
        .development-tree.ant-tree {
          color: #344054;
        }

        .development-tree .ant-tree-list-holder-inner {
          gap: 2px;
        }

        .development-tree .ant-tree-treenode {
          box-sizing: border-box;
          width: 100%;
          min-height: 32px;
          padding: 0 8px !important;
          align-items: center;
          border-radius: 0;
          transition: background-color 0.15s ease;
        }

        .development-tree .ant-tree-treenode:hover,
        .development-tree
          .ant-tree-treenode:has(.ant-tree-node-selected) {
          background: #f5f5f5;
        }

        .development-tree .ant-tree-node-content-wrapper {
          display: flex;
          min-width: 0;
          height: 32px;
          flex: 1;
          align-items: center;
          padding: 0 !important;
          border-radius: 0 !important;
          background: transparent !important;
          line-height: 32px;
        }

        .development-tree
          .ant-tree-node-content-wrapper.ant-tree-node-selected {
          color: #1f2937;
          background: transparent !important;
        }

        .development-tree .ant-tree-title {
          display: flex;
          min-width: 0;
          flex: 1;
        }

        .development-tree .ant-tree-indent-unit {
          width: 22px;
        }

        .development-tree .ant-tree-switcher {
          display: inline-flex;
          width: 20px;
          height: 32px;
          flex: none;
          align-items: center;
          justify-content: center;
          color: #98a2b3;
          line-height: 32px;
        }

        .development-tree .ant-tree-switcher svg {
          transition: transform 0.15s ease;
        }

        .development-tree .ant-tree-switcher_close svg {
          transform: rotate(-90deg);
        }

        .development-tree .ant-tree-switcher-noop {
          width: 20px;
        }
      `}</style>
    </>
  );
};

export default DevelopmentTreePane;
