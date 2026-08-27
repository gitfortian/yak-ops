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
  </header>
);

export default RealtimeSyncPageHeader;
