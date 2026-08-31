import DataCenter from './components/DataCenter';
import { HomeBackground } from './components/HomeBackground';
import { HomeHeader } from './components/HomeHeader';
import HomeWorkbench from './components/HomeWorkbench';
import NotificationCenter from './components/NotificationCenter';
import { QuickCreatePanel } from './components/QuickCreatePanel';
import ScheduleCenter from './components/ScheduleCenter';
import { useHomeCockpit } from './hooks/useHomeCockpit';

export default function HomePage() {
  const cockpit = useHomeCockpit();

  return (
    <div className="relative min-h-screen w-full overflow-hidden bg-[#f7f8fa] text-[#242731]">
      <HomeBackground />

      <div className="relative z-10">
        <HomeHeader stats={cockpit.data?.header} />

        <main className="px-4 pb-4">
          {/* 快速创建：全宽 */}
          <div className="mt-4">
            <QuickCreatePanel />
          </div>

          {/* 
            主体采用抖音创作者中心式双列布局。

            左：
            - 数据中心
            - HomeWorkbench

            右：
            - 通知
            - 调度中心

            两列高度完全独立，不再互相绑定。
          */}
          <div
            className="
              mt-4
              grid
              grid-cols-1
              items-start
              gap-4
              xl:grid-cols-[minmax(0,1fr)_380px]
              2xl:grid-cols-[minmax(0,1fr)_410px]
            "
          >
            {/* 左侧主内容 */}
            <div className="min-w-0 space-y-4">
              <DataCenter />

              <HomeWorkbench />
            </div>

            {/* 右侧辅助区域 */}
            <aside className="min-w-0 space-y-4">
              <NotificationCenter />

              <ScheduleCenter />
            </aside>
          </div>
        </main>
      </div>
    </div>
  );
}