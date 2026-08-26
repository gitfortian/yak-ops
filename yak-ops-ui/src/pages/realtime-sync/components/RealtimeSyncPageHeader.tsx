interface RealtimeSyncPageHeaderProps {
  streamConnected: boolean;
}

const RealtimeSyncPageHeader = ({
  streamConnected,
}: RealtimeSyncPageHeaderProps) => (
  <header>
    <h1 className="m-0 text-[17px] font-semibold leading-7 text-[#101828]">
      实时同步
    </h1>
    <div className="mt-0.5 text-[12px] text-[#98a2b3]">
      Flink CDC CLI + Flink REST · MySQL CDC → MySQL / PostgreSQL
      <span className="ml-2">
        · {streamConnected ? '状态流已连接' : '状态流重连中'}
      </span>
    </div>
  </header>
);

export default RealtimeSyncPageHeader;
