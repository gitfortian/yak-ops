import { LoaderCircle } from 'lucide-react';
import { useMemo, useState } from 'react';

import type {
  DevelopmentSqlResultColumn,
  DevelopmentSqlRunOutput,
} from '../../types';
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

const asSqlOutput = (value: Record<string, unknown>): DevelopmentSqlRunOutput =>
  value as DevelopmentSqlRunOutput;

const formatCell = (value: unknown) => {
  if (value === null) return 'NULL';
  if (value === undefined) return '';
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return String(value);
};

const columnTitle = (column: DevelopmentSqlResultColumn, index: number) =>
  column.label || column.name || `Column ${index + 1}`;

export const SqlRunResult = ({ result }: DevelopmentEditorRunResultContext) => {
  if (!result) {
    return (
      <div className="flex h-full items-center justify-center text-center">
        <div>
          <div className="text-[13px] font-medium text-[#475467]">SQL 运行结果</div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            点击顶部运行按钮执行当前编辑器中的 SQL
          </div>
        </div>
      </div>
    );
  }

  if (result.status === 'RUNNING') {
    return (
      <div className="flex h-full items-center justify-center text-[12px] text-[#667085]">
        <LoaderCircle size={16} className="mr-2 animate-spin" />
        正在执行 SQL…
      </div>
    );
  }

  if (result.status !== 'SUCCESS') {
    return (
      <div className="flex h-full items-center justify-center px-6 text-center">
        <div className="max-w-[680px]">
          <div className="text-[13px] font-medium text-[#b42318]">
            {result.status === 'CANCELLED'
              ? 'SQL 已取消'
              : result.status === 'TIMEOUT'
                ? 'SQL 执行超时'
                : 'SQL 执行失败'}
          </div>
          <div className="mt-2 break-words text-[11px] leading-5 text-[#667085]">
            {result.message || '数据库未返回更多错误信息'}
          </div>
          <div className="mt-2 text-[10px] text-[#98a2b3]">
            耗时 {result.durationMs} ms
          </div>
        </div>
      </div>
    );
  }

  const output = asSqlOutput(result.output);
  if (output.kind === 'UPDATE_COUNT') {
    const affectedRows =
      typeof output.affectedRows === 'number' && output.affectedRows >= 0
        ? output.affectedRows
        : '—';
    return (
      <div className="flex h-full items-center justify-center text-center">
        <div>
          <div className="text-[13px] font-medium text-[#344054]">SQL 执行完成</div>
          <div className="mt-2 text-[12px] text-[#475467]">
            影响行数：{affectedRows}
          </div>
          <div className="mt-1 text-[10px] text-[#98a2b3]">
            耗时 {result.durationMs} ms
          </div>
        </div>
      </div>
    );
  }

  const columns = Array.isArray(output.columns) ? output.columns : [];
  const rows = Array.isArray(output.rows) ? output.rows : [];

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden">
      <div className="flex h-8 shrink-0 items-center gap-4 border-b border-[#eef0f2] px-3 text-[10px] text-[#667085]">
        <span>{output.returnedRows ?? rows.length} 行</span>
        <span>{columns.length} 列</span>
        <span>{result.durationMs} ms</span>
        {output.truncated ? (
          <span className="text-[#b54708]">结果已达到返回上限</span>
        ) : null}
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
        {columns.length ? (
          <table className="min-w-full border-separate border-spacing-0 whitespace-nowrap text-[11px]">
            <thead className="sticky top-0 z-10 bg-[#fafafa] text-[#475467]">
              <tr>
                <th className="sticky left-0 z-20 w-12 border-b border-r border-[#e5e7eb] bg-[#fafafa] px-2 py-1.5 text-right font-medium text-[#98a2b3]">
                  #
                </th>
                {columns.map((column, index) => (
                  <th
                    key={`${column.name || column.label || 'column'}-${index}`}
                    title={column.typeName || undefined}
                    className="min-w-[120px] border-b border-r border-[#e5e7eb] px-2.5 py-1.5 text-left font-medium"
                  >
                    <span>{columnTitle(column, index)}</span>
                    {column.typeName ? (
                      <span className="ml-2 font-normal text-[#a4a9b2]">
                        {column.typeName}
                      </span>
                    ) : null}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="text-[#344054]">
              {rows.map((row, rowIndex) => (
                <tr key={rowIndex} className="hover:bg-[#fafafa]">
                  <td className="sticky left-0 border-b border-r border-[#eef0f2] bg-white px-2 py-1.5 text-right text-[#98a2b3]">
                    {rowIndex + 1}
                  </td>
                  {columns.map((column, columnIndex) => {
                    const value = row?.[columnIndex];
                    const display = formatCell(value);
                    return (
                      <td
                        key={`${column.name || columnIndex}-${columnIndex}`}
                        title={display}
                        className={[
                          'max-w-[420px] border-b border-r border-[#eef0f2] px-2.5 py-1.5',
                          value === null ? 'italic text-[#98a2b3]' : '',
                        ].join(' ')}
                      >
                        <div className="max-w-[400px] truncate">{display}</div>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="flex h-full items-center justify-center text-[11px] text-[#98a2b3]">
            SQL 执行成功，结果集没有可展示的字段
          </div>
        )}
      </div>
    </div>
  );
};
