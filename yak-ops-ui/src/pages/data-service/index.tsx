import DataServiceDetailNavigator from './components/DataServiceDetailNavigator';
import DataServiceMarketplaceHome from './components/DataServiceMarketplaceHome';
import DataServiceSearchResults from './components/DataServiceSearchResults';
import { useDataServiceMarketplace } from './hooks/useDataServiceMarketplace';

const DataServicePage = () => {
  const {
    services,
    dataSources,
    loading,
    keyword,
    submittedKeyword,
    detailTarget,
    callsByApiId,
    runningServices,
    recommendedServices,
    hotServices,
    searchResults,
    searching,
    totalCalls,
    dataSourceName,
    changeKeyword,
    search,
    resetSearch,
    openDetail,
    closeDetail,
    deleteService,
    toggleService,
    copyEndpoint,
    refresh,
  } = useDataServiceMarketplace();

  return (
    <div className="min-h-[calc(100vh-64px)] bg-white">
      {searching ? (
        <DataServiceSearchResults
          keyword={keyword}
          submittedKeyword={submittedKeyword}
          loading={loading}
          records={searchResults}
          callsByApiId={callsByApiId}
          dataSourceName={dataSourceName}
          onKeywordChange={changeKeyword}
          onSearch={search}
          onReset={resetSearch}
          onOpen={(service) => openDetail(service)}
          onCopyEndpoint={(endpoint) => void copyEndpoint(endpoint)}
          onToggle={(service, enabled) =>
            void toggleService(service, enabled)
          }
          onDelete={deleteService}
        />
      ) : (
        <DataServiceMarketplaceHome
          keyword={keyword}
          loading={loading}
          recommendedServices={recommendedServices}
          hotServices={hotServices}
          callsByApiId={callsByApiId}
          totalServices={services.length}
          runningServices={runningServices.length}
          totalCalls={totalCalls}
          dataSourceName={dataSourceName}
          onKeywordChange={changeKeyword}
          onSearch={search}
          onOpen={(service) => openDetail(service)}
        />
      )}

      <DataServiceDetailNavigator
        open={Boolean(detailTarget)}
        service={detailTarget}
        dataSources={dataSources}
        onClose={closeDetail}
        onChanged={refresh}
      />
    </div>
  );
};

export default DataServicePage;
