import { API_SUCCESS_CODE } from '@/services/http/response';
import { Spin, message } from 'antd';
import { FileCode2 } from 'lucide-react';
import { useEffect, useState } from 'react';

import {
  getDevelopmentTaskRevision,
  listDevelopmentTaskRevisions,
} from '../../service';
import type {
  DevelopmentNode,
  DevelopmentTaskRevision,
  DevelopmentTaskRevisionSummary,
} from '../../types';

interface TaskVersionsPanelProps {
  node: DevelopmentNode;
  refreshKey: number;
}

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString('zh-CN', { hour12: false });
};

const TaskVersionsPanel = ({ node, refreshKey }: TaskVersionsPanelProps) => {
  const [versions, setVersions] = useState<DevelopmentTaskRevisionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<DevelopmentTaskRevision>();

  useEffect(() => {
    let active = true;
    setLoading(true);
    setDetail(undefined);
    listDevelopmentTaskRevisions(node.id)
      .then((response) => {
        if (!active) return;
        setVersions(responseData(response, '查询发布版本失败') || []);
      })
      .catch((error) => {
        if (active) message.error(error instanceof Error ? error.message : '查询发布版本失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [node.id, refreshKey]);

  const openDetail = async (revisionNo: number) => {
    setDetailLoading(true);
    try {
      setDetail(
        responseData(
          await getDevelopmentTaskRevision(node.id, revisionNo),
          '查询版本详情失败',
        ),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询版本详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-24 items-center justify-center">
        <Spin size="small" />
      </div>
    );
  }

  if (!versions.length) {
    return (
      <div className="py-8 text-center text-[11px] leading-5 text-[#98a2b3]">
        暂无已发布版本
        <div className="mt-1">保存草稿后点击顶部“发布版本”即可生成 v1。</div>
      </div>
    );
  }

  return (
    <div className="space-y-3 text-[12px]">
      <div className="space-y-1.5">
        {versions.map((version, index) => (
          <button
            key={version.id}
            type="button"
            onClick={() => void openDetail(version.revisionNo)}
            className="flex w-full items-center gap-2 rounded-[3px] border border-[#eaecf0] px-2.5 py-2 text-left transition-colors hover:bg-[#f8f9fa]"
          >
            <FileCode2 size={14} className="shrink-0 text-[#667085]" strokeWidth={1.7} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium text-[#344054]">v{version.revisionNo}</span>
                {index === 0 ? (
                  <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[10px] text-[#667085]">
                    最新
                  </span>
                ) : null}
              </div>
              <div className="mt-0.5 truncate text-[10px] text-[#98a2b3]">
                {formatTime(version.createTime)} · {version.checksum.slice(0, 10)}
              </div>
            </div>
          </button>
        ))}
      </div>

      {detailLoading ? (
        <div className="flex h-16 items-center justify-center border-t border-[#eef0f2]">
          <Spin size="small" />
        </div>
      ) : detail ? (
        <div className="border-t border-[#eef0f2] pt-3">
          <div className="flex items-center justify-between gap-3">
            <span className="font-medium text-[#344054]">v{detail.revisionNo} 内容</span>
            <span className="text-[10px] text-[#98a2b3]">
              Draft #{detail.sourceDraftRevision}
            </span>
          </div>
          <pre className="mt-2 max-h-[300px] overflow-auto whitespace-pre-wrap break-words rounded-[3px] bg-[#f8f9fa] p-2.5 font-mono text-[11px] leading-5 text-[#475467]">
            {detail.definition.content || '(空内容)'}
          </pre>
          <div className="mt-2 break-all font-mono text-[9px] leading-4 text-[#b0b7c3]">
            SHA-256 {detail.checksum}
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default TaskVersionsPanel;
