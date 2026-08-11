import { Dropdown, Tooltip } from 'antd';
import { Check, MoreHorizontal, X } from 'lucide-react';
import { useEffect, useMemo, useRef } from 'react';

import { getEditorDefinition } from '../../editors/registry';
import {
  getEditorSession,
  useEditorSessionVersion,
} from '../../editors/session/editorSessionStore';
import type { DevelopmentId, DevelopmentNode } from '../../types';

export type EditorTabAction =
  | 'close-current'
  | 'close-others'
  | 'close-left'
  | 'close-right'
  | 'close-all';

interface EditorTabsProps {
  nodeMap: Map<DevelopmentId, DevelopmentNode>;
  openNodeIds: DevelopmentId[];
  activeNodeId?: DevelopmentId;
  onFocus: (nodeId: DevelopmentId) => void;
  onClose: (nodeId: DevelopmentId) => void;
  onAction: (action: EditorTabAction) => void;
}

const EditorTabs = ({
  nodeMap,
  openNodeIds,
  activeNodeId,
  onFocus,
  onClose,
  onAction,
}: EditorTabsProps) => {
  const tabRefs = useRef(new Map<DevelopmentId, HTMLDivElement>());
  const sessionVersion = useEditorSessionVersion();

  useEffect(() => {
    if (!activeNodeId) return;
    const frame = window.requestAnimationFrame(() => {
      tabRefs.current.get(activeNodeId)?.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest',
        inline: 'nearest',
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [activeNodeId]);

  const menuItems = useMemo(
    () => [
      {
        key: 'opened-editors',
        label: `已打开的编辑器（${openNodeIds.length}）`,
        children: openNodeIds.map((nodeId) => {
          const node = nodeMap.get(nodeId);
          const active = nodeId === activeNodeId;
          const definition = node ? getEditorDefinition(node.type) : undefined;
          const session = getEditorSession(nodeId);
          const Icon = definition?.icon;

          return {
            key: `focus:${nodeId}`,
            icon: Icon ? (
              <span className={definition?.iconClassName}>
                <Icon size={13} strokeWidth={1.8} />
              </span>
            ) : undefined,
            label: (
              <div className="flex min-w-[190px] items-center justify-between gap-3">
                <span className="flex min-w-0 items-center gap-2">
                  <span className="max-w-[200px] truncate">
                    {node?.name || nodeId}
                  </span>
                  {session?.dirty ? (
                    <span
                      className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#667085]"
                      title="未保存"
                    />
                  ) : null}
                </span>
                {active ? (
                  <Check size={13} className="shrink-0 text-[#667085]" />
                ) : null}
              </div>
            ),
          };
        }),
      },
      { type: 'divider' as const },
      { key: 'close-current', label: '关闭当前编辑器' },
      {
        key: 'close-others',
        label: '关闭其他编辑器',
        disabled: openNodeIds.length <= 1,
      },
      {
        key: 'close-left',
        label: '关闭左侧编辑器',
        disabled: !activeNodeId || openNodeIds.indexOf(activeNodeId) <= 0,
      },
      {
        key: 'close-right',
        label: '关闭右侧编辑器',
        disabled:
          !activeNodeId ||
          openNodeIds.indexOf(activeNodeId) >= openNodeIds.length - 1,
      },
      { key: 'close-all', label: '全部关闭' },
    ],
    [activeNodeId, nodeMap, openNodeIds, sessionVersion],
  );

  return (
    <div className="flex h-9 shrink-0 border-b border-[#e8e9ec] bg-[#f7f7f8]">
      <div className="min-w-0 flex-1 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        <div className="flex h-9 min-w-max items-stretch">
          {openNodeIds.map((nodeId) => {
            const node = nodeMap.get(nodeId);
            if (!node) return null;
            const active = nodeId === activeNodeId;
            const definition = getEditorDefinition(node.type);
            const session = getEditorSession(nodeId);
            const Icon = definition.icon;

            return (
              <div
                key={nodeId}
                ref={(element) => {
                  if (element) tabRefs.current.set(nodeId, element);
                  else tabRefs.current.delete(nodeId);
                }}
                onAuxClick={(event) => {
                  if (event.button === 1) onClose(nodeId);
                }}
                className={[
                  'group relative flex h-9 min-w-[120px] max-w-[220px] flex-none items-center border-b border-b-transparent border-r border-r-[#e5e7eb] border-t-2 transition-colors',
                  active
                    ? 'z-10 border-b-white border-t-[rgba(254,44,85,1)] bg-white text-[#344054]'
                    : 'border-t-transparent bg-[#f7f7f8] text-[#667085] hover:bg-[#f0f1f2] hover:text-[#344054]',
                ].join(' ')}
              >
                <button
                  type="button"
                  title={node.name}
                  aria-current={active ? 'page' : undefined}
                  onClick={() => onFocus(nodeId)}
                  className="flex h-full min-w-0 flex-1 items-center gap-2 bg-transparent pl-3 pr-1 text-left outline-none"
                >
                  <span
                    className={[
                      'flex h-5 w-4 shrink-0 items-center justify-center',
                      definition.iconClassName,
                    ].join(' ')}
                  >
                    <Icon size={13} strokeWidth={1.8} />
                  </span>
                  <span
                    className={[
                      'min-w-0 flex-1 truncate text-[12px] leading-5',
                      active ? 'font-medium text-[#344054]' : 'font-normal',
                    ].join(' ')}
                  >
                    {node.name}
                  </span>
                  {session?.dirty ? (
                    <span
                      className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#667085]"
                      title="未保存"
                    />
                  ) : null}
                </button>

                <button
                  type="button"
                  aria-label={`关闭 ${node.name}`}
                  title="关闭"
                  onClick={() => onClose(nodeId)}
                  className={[
                    'mr-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-[3px] text-[#98a2b3] transition-all',
                    active
                      ? 'opacity-100 hover:bg-[#f2f4f7] hover:text-[#475467]'
                      : 'opacity-0 group-hover:opacity-100 hover:bg-[#e4e7ec] hover:text-[#475467]',
                  ].join(' ')}
                >
                  <X size={13} strokeWidth={1.8} />
                </button>
              </div>
            );
          })}
        </div>
      </div>

      <div className="flex h-9 w-10 shrink-0 items-center justify-center border-l border-[#e5e7eb] bg-[#f7f7f8]">
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: menuItems,
            onClick: ({ key }) => {
              if (key.startsWith('focus:')) {
                onFocus(key.substring('focus:'.length));
                return;
              }
              onAction(key as EditorTabAction);
            },
          }}
        >
          <Tooltip title="编辑器操作" placement="bottomRight">
            <button
              type="button"
              aria-label="编辑器操作"
              className="flex h-7 w-7 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-white hover:text-[#344054]"
            >
              <MoreHorizontal size={17} strokeWidth={1.8} />
            </button>
          </Tooltip>
        </Dropdown>
      </div>
    </div>
  );
};

export default EditorTabs;
