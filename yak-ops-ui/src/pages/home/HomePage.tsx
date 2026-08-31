import DataCenter from './components/DataCenter';
import { HomeBackground } from './components/HomeBackground';
import { HomeHeader } from './components/HomeHeader';
import {
  HomeWorkbenchMain,
  HomeWorkbenchSidebar,
} from './components/HomeWorkbench';
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
          <div className="mt-4">
            <QuickCreatePanel />
          </div>

          <div className="mt-4 grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_380px] 2xl:grid-cols-[minmax(0,1fr)_410px]">
            <div className="min-w-0 space-y-4">
              <DataCenter />
              <HomeWorkbenchMain />
            </div>

            <aside className="min-w-0 space-y-4">
              <NotificationCenter />
              <ScheduleCenter />
              <HomeWorkbenchSidebar />
            </aside>
          </div>
        </main>
      </div>
    </div>
  );
}
