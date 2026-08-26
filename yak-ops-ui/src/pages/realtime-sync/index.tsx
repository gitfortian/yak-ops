import { history } from '@umijs/max';
import { ConfigProvider, Divider } from 'antd';

import CreateRealtimeTaskDrawer from './components/CreateRealtimeTaskDrawer';
import RealtimeSyncCapabilityBar from './components/RealtimeSyncCapabilityBar';
import RealtimeSyncFilterBar from './components/RealtimeSyncFilterBar';
import RealtimeSyncPageHeader from './components/RealtimeSyncPageHeader';
import RealtimeSyncRuntimeDrawer from './components/RealtimeSyncRuntimeDrawer';
import RealtimeSyncTaskTable from './components/RealtimeSyncTaskTable';
import { REALTIME_SYNC_PAGE_THEME } from './constants';
import { useRealtimeSyncPage } from './hooks/useRealtimeSyncPage';
import { getRealtimeEditPath } from './utils';

const RealtimeSyncPage = () => {
  const {
    jobs,
    pagination,
    loading,
    capabilities,
    dataSourceMap,
    environmentMap,
    filterDraft,
    filters,
    createOpen,
    detail,
    events,
    streamConnected,
    setCreateOpen,
    updateFilterDraft,
    searchTasks,
    changeStateGroup,
    changeReleaseState,
    resetFilters,
    changePagination,
    refresh,
    performAction,
    openDetail,
    closeDetail,
    deleteTask,
    copyTaskId,
  } = useRealtimeSyncPage();

  return (
    <ConfigProvider theme={REALTIME_SYNC_PAGE_THEME}>
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
        <RealtimeSyncPageHeader streamConnected={streamConnected} />

        <div className="mx-auto mt-3 flex w-full max-w-full flex-1 flex-col">
          <div className="mb-3">
            <RealtimeSyncFilterBar
              filterDraft={filterDraft}
              activeStateGroup={filters.stateGroup}
              advancedFilterCount={filterDraft.id ? 1 : 0}
              onDraftChange={updateFilterDraft}
              onStateGroupChange={changeStateGroup}
              onReleaseStateChange={changeReleaseState}
              onSearch={searchTasks}
              onReset={resetFilters}
            />

            <RealtimeSyncCapabilityBar
              capabilities={capabilities}
              loading={loading}
              onRefresh={() => void refresh()}
              onCreate={() => setCreateOpen(true)}
            />
          </div>

          <Divider style={{ marginTop: 4, marginBottom: 16 }} />

          <RealtimeSyncTaskTable
            jobs={jobs}
            loading={loading}
            pagination={pagination}
            dataSourceMap={dataSourceMap}
            environmentMap={environmentMap}
            onPaginationChange={changePagination}
            onCopyTaskId={(id) => void copyTaskId(id)}
            onEdit={(job) => history.push(getRealtimeEditPath(job))}
            onDetail={(job) => void openDetail(job)}
            onDelete={deleteTask}
            onAction={performAction}
          />
        </div>

        <CreateRealtimeTaskDrawer
          open={createOpen}
          onClose={() => setCreateOpen(false)}
        />

        <RealtimeSyncRuntimeDrawer
          job={detail}
          events={events}
          onClose={closeDetail}
        />
      </div>
    </ConfigProvider>
  );
};

export default RealtimeSyncPage;
