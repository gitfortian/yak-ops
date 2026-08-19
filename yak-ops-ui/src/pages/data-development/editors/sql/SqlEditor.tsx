import { useMemo, useState } from 'react';

import SqlResultWorkspace from '../../components/sql-result/SqlResultWorkspace';
import {
  updateEditorSessionContent,
  updateEditorSessionViewState,
  useEditorSession,
} from '../session/editorSessionStore';
import type {
  DevelopmentEditorContext,
  DevelopmentEditorRunResultContext,
} from '../types';
import SqlMonacoEditor, {
  type SqlEditorPosition,
} from './components/SqlMonacoEditor';
import { useSqlMetadataContext } from './metadata/sqlMetadataContextStore';

const defaultPosition: SqlEditorPosition = {
  lineNumber: 1,
  column: 1,
  selectionLength: 0,
};

export const SqlEditor = ({
  node,
  onRunContent,
  running,
}: DevelopmentEditorContext) => {
  const session = useEditorSession(node.id, node.type);
  const metadataContext = useSqlMetadataContext(node.id);
  const [position, setPosition] = useState<SqlEditorPosition>(() => ({
    lineNumber: session.viewState?.lineNumber || 1,
    column: session.viewState?.column || 1,
    selectionLength: 0,
  }));

  const metadataPath = useMemo(
    () =>
      [
        metadataContext.dataSourceName ||
          (metadataContext.dataSourceId
            ? `DS ${metadataContext.dataSourceId}`
            : undefined),
        metadataContext.database,
        metadataContext.schema,
      ]
        .filter(Boolean)
        .join(' / '),
    [
      metadataContext.dataSourceId,
      metadataContext.dataSourceName,
      metadataContext.database,
      metadataContext.schema,
    ],
  );

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-white">
      <div className="min-h-0 flex-1">
        <SqlMonacoEditor
          id={String(node.id)}
          value={session.content}
          initialViewState={session.viewState}
          onChange={(value) => updateEditorSessionContent(node.id, value)}
          onRunStatement={onRunContent}
          running={running}
          onPositionChange={setPosition}
          onViewStateChange={(viewState) =>
            updateEditorSessionViewState(node.id, viewState)
          }
        />
      </div>

      <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#7b808a]">
        <div className="flex min-w-0 items-center gap-3">
          <span className="font-medium text-[#667085]">SQL</span>
          <span className="truncate">{node.name}</span>
          {session.dirty ? (
            <span className="inline-flex shrink-0 items-center gap-1 text-[#667085]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#667085]" />
              未保存
            </span>
          ) : null}
          <span
            className={[
              'max-w-[260px] truncate',
              metadataContext.dataSourceId ? 'text-[#667085]' : 'text-[#b0b7c3]',
            ].join(' ')}
            title={metadataPath || '未选择数据源'}
          >
            {metadataPath || '未选择数据源'}
          </span>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          {position.selectionLength > 0 ? (
            <span>已选择 {position.selectionLength} 字符</span>
          ) : null}
          <span>
            Ln {position.lineNumber}, Col {position.column}
          </span>
        </div>
      </div>
    </div>
  );
};

export const SqlRunConfig = ({ node }: DevelopmentEditorContext) => (
  <div className="text-[12px] leading-6 text-[#667085]">
    <div className="font-medium text-[#344054]">SQL 运行配置</div>
    <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
      {node.name} 的数据源、Database、Schema 由编辑器工具栏右侧选择，运行时通过数据源插件解析真实连接。
    </div>
    <div className="mt-3 border-t border-[#eef0f2] pt-3 text-[11px] leading-5 text-[#98a2b3]">
      当前默认最多返回 200 行、执行超时 30 秒；Task Plugin 已预留 maxRows 和 timeoutSeconds 配置字段，后续可开放到运行配置面板。
    </div>
  </div>
);

export const SqlRunResult = ({ result }: DevelopmentEditorRunResultContext) => (
  <SqlResultWorkspace result={result} />
);
