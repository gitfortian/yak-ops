import { CodeOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import type { RealtimeJob } from './types';

export default function YamlJobEditor({
  job,
  onClose,
}: {
  job: RealtimeJob;
  onClose: () => void;
}) {
  return (
    <div className="min-h-[calc(100vh-64px)] bg-[#f7f8fa] px-6 py-6 text-[#161823]">
      <div className="mx-auto w-full max-w-[1120px]">
        <div className="mb-5 flex items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-[12px] font-medium text-[#ff4d4f]">
              <CodeOutlined />
              YAML 模式
            </div>
            <h1 className="mb-0 mt-1 text-[20px] font-semibold text-[#101828]">{job.name}</h1>
            <div className="mt-1 text-[12px] text-[#98a2b3]">任务 ID：{job.id} · 当前为基础草稿</div>
          </div>
          <Button onClick={onClose}>返回任务列表</Button>
        </div>

        <section className="rounded-xl bg-white p-7">
          <div className="rounded-xl border border-dashed border-[#d0d5dd] bg-[#fcfcfd] px-6 py-10 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-[#fff1f0] text-[22px] text-[#ff4d4f]">
              <CodeOutlined />
            </div>
            <div className="mt-4 text-[16px] font-semibold text-[#101828]">YAML 编辑入口已建立</div>
            <div className="mx-auto mt-2 max-w-[620px] text-[13px] leading-6 text-[#667085]">
              流程 1 只负责创建方式和编辑入口分流。YAML 与 CdcPipelineSpec 的解析、序列化、校验和保存能力将在后续 YAML 流程中接入。
            </div>
          </div>

          <div className="mt-5 rounded-lg bg-[#f9fafb] px-5 py-4 text-[12px] leading-6 text-[#667085]">
            当前不会持久化原生 Flink CDC YAML，也不会改变现有任务 Spec、发布、启动、停止、SSH 提交和可观测链路。
          </div>
        </section>
      </div>
    </div>
  );
}
