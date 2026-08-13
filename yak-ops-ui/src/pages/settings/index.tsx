import { SlidersHorizontal } from 'lucide-react';

import EditorSettingsPanel from './components/EditorSettingsPanel';

const SettingsPage = () => (
  <div className="flex min-h-[calc(100vh-80px)] bg-white">
    <aside className="w-[196px] shrink-0 border-r border-[#eaecf0] bg-[#fafafa] px-3 py-5">
      <div className="px-3 pb-3 text-[12px] font-medium text-[#98a2b3]">设置</div>
      <button
        type="button"
        aria-current="page"
        className="flex h-10 w-full items-center gap-2.5 rounded-md border-0 bg-[#f0f1f3] px-3 text-left text-[13px] font-semibold text-[#161823]"
      >
        <SlidersHorizontal size={15} strokeWidth={1.8} />
        <span>编辑器设置</span>
      </button>
    </aside>

    <section className="min-w-0 flex-1 overflow-auto">
      <div className="mx-auto w-full max-w-[920px] px-10 py-8 xl:px-14">
        <EditorSettingsPanel />
      </div>
    </section>
  </div>
);

export default SettingsPage;
