import { SendOutlined } from "@ant-design/icons";
import { Select } from "antd";
import MysqlIcon from "@/components/data-source/icons/MysqlIcon";
import OracleIcon from "@/components/data-source/icons/OracleIcon";
import PostgreSQL from "@/components/data-source/icons/PsSqlIcon";
import DorisIcon from "@/components/data-source/icons/DorisIcon";
import KingBaseIcon from "@/components/data-source/icons/KingBaseIcon";
import DaMengIcon from "@/components/data-source/icons/DamengIcon";
import { defaultOfflineSyncConnectorProfile } from "./connectorProfiles";
import "./index.less";

// 类型定义
interface DataSourceType {
  value: string;
  label: React.ReactNode;
  icon?: React.ReactNode;
  connectorType?: string;
  pluginName?: string;
}

const executionMetadata = (dbType: string) => {
  const profile = defaultOfflineSyncConnectorProfile(dbType);
  return {
    connectorType: profile?.connectorType,
    pluginName: profile?.pluginName,
  };
};

// 生成数据源选项配置。图标/文案属于 UI；执行 Connector 元数据统一来自 Profile Registry。
export const generateDataSourceOptions = (): DataSourceType[] => [
  {
    value: "MYSQL",
    ...executionMetadata("MYSQL"),
    label: (
      <div style={{ display: "flex", alignItems: "center" }}>
        <MysqlIcon height="24px" width="24px" />
        <span style={{ marginLeft: 8 }}>MYSQL</span>
      </div>
    ),
  },
  {
    value: "ORACLE",
    ...executionMetadata("ORACLE"),
    label: (
      <div style={{ display: "flex", alignItems: "center" }}>
        <OracleIcon />
        <span style={{ marginLeft: 8 }}>ORACLE</span>
      </div>
    ),
  },
  {
    value: "POSTGRE_SQL",
    ...executionMetadata("POSTGRE_SQL"),
    label: (
      <div style={{ display: "flex", alignItems: "center" }}>
        <PostgreSQL />
        <span style={{ marginLeft: 8 }}>PostGreSQL</span>
      </div>
    ),
  },
  {
    value: "DORIS",
    ...executionMetadata("DORIS"),
    label: (
      <div style={{ display: "flex", alignItems: "center" }}>
        <DorisIcon />
        <span style={{ marginLeft: 8 }}>Doris</span>
      </div>
    ),
  },
  {
    value: "KINGBASE",
    ...executionMetadata("KINGBASE"),
    label: (
      <div style={{ display: "flex", alignItems: "center" }}>
        <KingBaseIcon />
        <span style={{ marginLeft: 8 }}>KINGBASE</span>
      </div>
    ),
  },
  {
    value: "DAMENG",
    ...executionMetadata("DAMENG"),
    label: (
      <div style={{ display: "flex", alignItems: "center" }}>
        <DaMengIcon />
        <span style={{ marginLeft: 8 }}>DAMENG</span>
      </div>
    ),
  },
];

// 数据源选择器组件
interface DataSourceSelectProps {
  value: any;
  onChange: (value: string, option: any) => void;
  placeholder: string;
  prefix: string;
  dataSourceOptions: any[];
  width?: string;
}

export const DataSourceSelect: React.FC<DataSourceSelectProps> = ({
  value,
  onChange,
  placeholder,
  prefix,
  dataSourceOptions,
  width = "42%",
}) => {
  return (
    <Select
      showSearch
      className="custom-ant-select-selector"
      placeholder={placeholder}
      value={value?.dbType}
      optionFilterProp="label"
      onChange={onChange}
      suffixIcon={<SendOutlined />}
      style={{ width: width, borderRadius: 24 }}
      prefix={<span style={{ fontSize: 12,fontWeight: 500 }}>{prefix}</span>}
      filterOption={(input, option) => {
        const labelText =
          typeof option?.label === "string" ? option.label : "MYSQL";
        return labelText.toLowerCase().includes(input.toLowerCase());
      }}
      options={dataSourceOptions}
    />
  );
};

export default DataSourceSelect;
