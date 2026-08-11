import YakEmpty from '@/components/YakEmpty';
import type { DataNode } from 'antd/es/tree';
import type { MenuProps, TreeProps } from 'antd';
import { Button, Dropdown, Input, Spin, Tooltip, Tree } from 'antd';
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Code2,
  Copy,
  FileText,
  Folder,
  FolderPlus,
  Pencil,
  Plus,
  Search,
  TerminalSquare,
  Trash2,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';

export type DevelopmentTreeNodeType = 'directory' | 'node';
export type DevelopmentNodeCreateType = 'SQL' | 'SHELL';
export type DevelopmentTreeAction =
  | 'create-directory'
  | 'create-sql'
  | 'create-shell'
  | 'copy-name'
  | 'copy-path'
  | 'rename'
  | 'delete';

export interface DevelopmentTreeNode extends DataNode {
  key: string;
  title: string;
  nodeType: DevelopmentTreeNodeType;
  resourceId: string;
  resourcePath: string;
  taskType?: string;
  searchText?: string;
  children?: DevelopmentTreeNode[];
}

interface DevelopmentTreePaneProps {
  treeData: DevelopmentTreeNode[];
  treeLoading: boolean;
  selectedNodeKey?: string;
  searchValue: string;
  leftWidth: number;
  collapsed: boolean;
  onCreateDirectory: () => void;
  onCreateNode: (type: DevelopmentNodeCreateType) => void;
  onResourceAction: (
    action: DevelopmentTreeAction,
    node: DevelopmentTreeNode,
  ) => void;
  onSearchChange: (value: string) => void;
  onSelect: TreeProps['onSelect'];
  onResizeStart: (event: ReactPointerEvent) => void;
  onCollapsedChange: (collapsed: boolean) => void;
}

const DevelopmentTreePane = ({
  treeData,
  treeLoading,
  selectedNodeKey,
  searchValue,
  leftWidth,
  collapsed,
  onCreateDirectory,
  onCreateNode,
  onResourceAction,
  onSearchChange,
  onSelect,
  onResizeStart,
  onCollapsedChange,
}: DevelopmentTreePaneProps) => {
  const createMenuItems: MenuProps['items'] = [
    {
      key: 'node',
      label: '新建节点',
      icon: <Code2 size={14} strokeWidth={1.8} />,
      children: [
        {
          key: 'node-sql',
          label: 'SQL 节点',
          icon: <Code2 size={14} strokeWidth={1.8} />,
        },
        {
          key: 'node-shell',
          label: 'Shell 节点',
          icon: <TerminalSquare size={14} strokeWidth={1.8} />,
        },
      ],
    },
    {
      key: 'directory',
      label: '新建目录',
      icon: <FolderPlus size={14} strokeWidth={1.8} />,
    },
  ];

  const contextMenuItems = (node: DevelopmentTreeNode): MenuProps['items'] => {
    const commonItems: MenuProps['items'] = [
      {
        key: 'copy-name',
        label: '复制名称',
        icon: <Copy size={14} strokeWidth={1.8} />,
      },
      {
        key: 'copy-path',
        label: '复制路径',
        icon: <FileText size={14} strokeWidth={1.8} />,
      },
      { type: 'divider' },
      {
        key: 'rename',
        label: '重命名',
        icon: <Pencil size={14} strokeWidth={1.8} />,
      },
      {
        key: 'delete',
        label: '删除',
        icon: <Trash2 size={14} strokeWidth={1.8} />,
        danger: true,
      },
    ];

    if (node.nodeType !== 'directory') return commonItems;
    return [
      {
        key: 'create-node',
        label: '新建节点',
        icon: <Code2 size={14} strokeWidth={1.8} />,
        children: [
          {
            key: 'create-sql',
            label: 'SQL 节点',
            icon: <Code2 size={14} strokeWidth={1.8} />,
          },
          {
            key: 'create-shell',
            label: 'Shell 节点',
            icon: <TerminalSquare size={14} strokeWidth={1.8} />,
          },
        ],
      },
      {
        key: 'create-directory',
        label: '新建目录',
        icon: <FolderPlus size={14} strokeWidth={1.8} />,
      },
      { type: 'divider' },
      ...commonItems,
    ];
  };

  const renderTitle: TreeProps['titleRender'] = (rawNode) => {
    const node = rawNode as DevelopmentTreeNode;
    const isNode = node.nodeType === 'node';

    return (
      <Dropdown
        trigger={['contextMenu']}
        menu={{
          items: contextMenuItems(node),
          triggerSubMenuAction: 'hover',
          subMenuOpenDelay: 0.05,
          subMenuCloseDelay: 0.1,
          onClick: ({ key }) =>
            onResourceAction(key as DevelopmentTreeAction, node),
        }}
      >
        <div className="flex min-w-0 flex-1 items-center gap-2" title={node.title}>
          {isNode ? (
            node.taskType === 'SHELL' ? (
              <TerminalSquare
                size={13}
                strokeWidth={1.8}
                className="shrink-0 text-[#667085]"
              />
            ) : (
              <Code2
                size={13}
                strokeWidth={1.8}
                className="shrink-0 text-[#667085]"
              />
            )
          ) : (
            <Folder
              size={14}
              strokeWidth={1.8}
              className="shrink-0 text-[#98a2b3]"
            />
          )}

          <span
            className={[
              'min-w-0 flex-1 truncate text-[13px] leading-8',
              isNode ? 'font-normal text-[#344054]' : 'font-medium text-[#1f2937]',
            ].join(' ')}
          >
            {node.title}
          </span>

          {isNode && node.taskType ? (
            <span className="shrink-0 text-[10px] text-[#98a2b3]">
              {node.taskType}
            </span>
          ) : null}
        </div>
      </Dropdown>
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
          className="flex h-full flex-col overflow-hidden"
          style={{ width: leftWidth }}
        >
          <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e5e7eb] bg-[#f5f5f6] px-3">
            <span className="text-[13px] font-semibold text-[#30323b]">
              开发目录
            </span>

            <Dropdown
              trigger={['click']}
              placement="bottomRight"
              menu={{
                items: createMenuItems,
                triggerSubMenuAction: 'hover',
                subMenuOpenDelay: 0.05,
                subMenuCloseDelay: 0.1,
                onClick: ({ key }) => {
                  if (key === 'directory') onCreateDirectory();
                  if (key === 'node-sql') onCreateNode('SQL');
                  if (key === 'node-shell') onCreateNode('SHELL');
                },
              }}
            >
              <Tooltip title="新建" placement="right">
                <Button
                  type="text"
                  size="small"
                  aria-label="新建目录或节点"
                  icon={<Plus size={15} strokeWidth={1.8} />}
                  className="!flex !h-7 !w-7 !items-center !justify-center !p-0 hover:!bg-white"
                />
              </Tooltip>
            </Dropdown>
          </div>

          <div className="flex h-11 shrink-0 items-center border-b border-[#e8e9ec] px-3">
            <Input
              allowClear
              size="small"
              variant="filled"
              value={searchValue}
              prefix={<Search size={13} className="text-[#98a2b3]" />}
              placeholder="搜索名称 / 节点"
              onChange={(event) => onSearchChange(event.target.value)}
            />
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
            <Spin spinning={treeLoading} wrapperClassName="block min-h-full">
              {treeData.length ? (
                <Tree
                  blockNode
                  defaultExpandAll
                  autoExpandParent={Boolean(searchValue.trim())}
                  selectedKeys={selectedNodeKey ? [selectedNodeKey] : []}
                  treeData={treeData}
                  titleRender={renderTitle}
                  switcherIcon={<ChevronDown size={12} strokeWidth={1.8} />}
                  onSelect={onSelect}
                  className="development-tree bg-transparent"
                />
              ) : (
                <YakEmpty
                  compact
                  title={searchValue.trim() ? '未找到匹配节点' : '暂无开发节点'}
                  description={
                    searchValue.trim()
                      ? '换个关键词试试'
                      : '点击右上角 + 创建目录或节点'
                  }
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
          'group relative z-20 w-px shrink-0 touch-none',
          collapsed ? 'cursor-default' : 'cursor-col-resize',
        ].join(' ')}
      >
        <div
          className={[
            'absolute inset-y-0 left-1/2 z-10 w-3 -translate-x-1/2',
            collapsed ? 'cursor-default' : 'cursor-col-resize',
          ].join(' ')}
        />

        <div
          className={[
            'pointer-events-none absolute inset-y-0 left-0',
            'w-px bg-[#dfe3e8]',
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
            'absolute left-px top-1/2 z-20',
            'flex h-7 w-3 -translate-y-1/2',
            'items-center justify-center rounded-r-[3px]',
            'border border-l-0 border-[#dfe3e8] bg-white text-[#7b808a]',
            'shadow-[0_1px_2px_rgba(16,24,40,0.04)]',
            'opacity-0 transition-[opacity,color,border-color,box-shadow] duration-150',
            'group-hover:opacity-100 focus:opacity-100',
            'hover:border-[#cfd4dc] hover:text-[#344054]',
            'focus:outline-none focus-visible:ring-2',
            'focus-visible:ring-[rgba(254,44,85,.16)]',
          ].join(' ')}
        >
          {collapsed ? <ChevronRight size={11} /> : <ChevronLeft size={11} />}
        </button>
      </div>

      <style>{`
        .development-tree.ant-tree { color: #344054; }
        .development-tree .ant-tree-list-holder-inner { gap: 1px; }
        .development-tree .ant-tree-treenode {
          box-sizing: border-box;
          width: 100%;
          min-height: 30px;
          padding: 0 6px !important;
          align-items: center;
          border-radius: 0;
          transition: background-color 0.15s ease;
        }
        .development-tree .ant-tree-treenode:hover,
        .development-tree .ant-tree-treenode:has(.ant-tree-node-selected) { background: #f5f5f5; }
        .development-tree .ant-tree-node-content-wrapper {
          display: flex;
          min-width: 0;
          height: 30px;
          flex: 1;
          align-items: center;
          padding: 0 !important;
          border-radius: 0 !important;
          background: transparent !important;
          line-height: 30px;
        }
        .development-tree .ant-tree-node-content-wrapper.ant-tree-node-selected {
          color: #1f2937;
          background: transparent !important;
        }
        .development-tree .ant-tree-title { display: flex; min-width: 0; flex: 1; }
        .development-tree .ant-tree-indent-unit { width: 18px; }
        .development-tree .ant-tree-switcher {
          display: inline-flex;
          width: 18px;
          height: 30px;
          flex: none;
          align-items: center;
          justify-content: center;
          color: #98a2b3;
          line-height: 30px;
        }
        .development-tree .ant-tree-switcher svg { transition: transform 0.15s ease; }
        .development-tree .ant-tree-switcher_close svg { transform: rotate(-90deg); }
        .development-tree .ant-tree-switcher-noop { width: 18px; }
      `}</style>
    </>
  );
};

export default DevelopmentTreePane;
