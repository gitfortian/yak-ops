import {
  Activity,
  ArrowRightLeft,
  Boxes,
  Database,
  Gauge,
  LayoutGrid,
  Map as MapIcon,
  Network,
  Settings,
  ShieldCheck,
  Tags,
  Users,
  Wrench,
  type LucideIcon,
} from "lucide-react";

/** A nested entry is text-only and one level deep, indented under its group. */
export type ProductNavigationChild = {
  label: string;
  path: string;
  /** Paths outside `path` that still belong to this entry, such as a detail route under a list. */
  activePrefixes?: string[];
};

export type ProductNavigationItem = {
  label: string;
  path: string;
  icon: LucideIcon;
  groupLabel?: string;
  /** When present the item is a collapsible group rather than a link. */
  children?: ProductNavigationChild[];
};

type GlobalProductMenuItem = {
  id: string;
  label: string;
  icon: LucideIcon;
  path: string;
};

type AllProductGroup = {
  id: string;
  label: string;
  products: GlobalProductMenuItem[];
};

export const DATA_INTEGRATION_PRODUCT_LABEL = "数据集成";
export const OPERATIONS_PRODUCT_LABEL = "运维中心";
export const MANAGEMENT_PRODUCT_LABEL = "管理中心";
export const DATA_MODELING_PRODUCT_LABEL = "数据建模";
export const DATA_GOVERNANCE_PRODUCT_LABEL = "数据治理";

export const DATA_MODELING_NAVIGATION: ProductNavigationItem[] = [
  { label: "数仓规划", path: "/data-modeling/warehouse-planning", icon: Network },
  { label: "数据标准", path: "/data-modeling/data-standards", icon: Database },
  { label: "数据指标", path: "/data-modeling/metrics", icon: Gauge },
  { label: "维度建模", path: "/data-modeling/dimension-modeling", icon: Boxes },
];

export const DATA_INTEGRATION_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "数据源管理",
    path: "/data-source",
    icon: Database,
  },
  {
    label: "数据同步",
    path: "/data-sync",
    icon: ArrowRightLeft,
  },
];

/**
 * A governance module is one navigation entry; later modules join this array, not a new shell.
 * Its pages are children of that entry, so the product keeps one sidebar row as modules are added.
 */
export const DATA_GOVERNANCE_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "数据地图",
    path: "/data-governance/data-map",
    icon: MapIcon,
    children: [
      { label: "探索首页", path: "/data-governance/data-map/overview" },
      {
        label: "资产目录",
        path: "/data-governance/data-map/catalog",
        activePrefixes: ["/data-governance/data-map/tables"],
      },
      {
        label: "元数据采集",
        path: "/data-governance/data-map/tasks",
        activePrefixes: ["/data-governance/data-map/crawls"],
      },
      { label: "我的数据", path: "/data-governance/data-map/personal" },
    ],
  },
  {
    // The dictionary is shared vocabulary, not catalog browsing, so it is a sibling entry.
    label: "标签管理",
    path: "/data-governance/tags",
    icon: Tags,
  },
  {
    label: "资产治理",
    path: "/data-governance/governance/overview",
    icon: ShieldCheck,
    children: [
      { label: "治理总览", path: "/data-governance/governance/overview" },
      {
        label: "治理问题",
        path: "/data-governance/governance/issues",
        activePrefixes: ["/data-governance/governance/issues"],
      },
      { label: "治理规则", path: "/data-governance/governance/rules" },
    ],
  },
];

export const OPERATIONS_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "离线任务",
    path: "/operations/offline-tasks",
    icon: ArrowRightLeft,
    groupLabel: "任务运维",
  },
  {
    label: "实时任务",
    path: "/operations/realtime-tasks",
    icon: Activity,
    groupLabel: "任务运维",
  },
];

export const MANAGEMENT_NAVIGATION: ProductNavigationItem[] = [
  {
    label: "用户管理",
    path: "/management/users",
    icon: Users,
  },
  {
    label: "工作空间管理",
    path: "/management/workspaces",
    icon: LayoutGrid,
  },
];

const DATA_INTEGRATION_PRODUCT: GlobalProductMenuItem = {
  id: "data-integration",
  label: DATA_INTEGRATION_PRODUCT_LABEL,
  icon: Database,
  path: "/data-source",
};

const OPERATIONS_PRODUCT: GlobalProductMenuItem = {
  id: "operations",
  label: OPERATIONS_PRODUCT_LABEL,
  icon: Wrench,
  path: "/operations/offline-tasks",
};

const MANAGEMENT_PRODUCT: GlobalProductMenuItem = {
  id: "management",
  label: MANAGEMENT_PRODUCT_LABEL,
  icon: Settings,
  path: "/management/users",
};

const DATA_MODELING_PRODUCT: GlobalProductMenuItem = {
  id: "data-modeling",
  label: DATA_MODELING_PRODUCT_LABEL,
  icon: Network,
  path: "/data-modeling/warehouse-planning",
};

const DATA_GOVERNANCE_PRODUCT: GlobalProductMenuItem = {
  id: "data-governance",
  label: DATA_GOVERNANCE_PRODUCT_LABEL,
  icon: MapIcon,
  path: "/data-governance/data-map/overview",
};

export const GLOBAL_PRODUCT_MENU: GlobalProductMenuItem[] = [
  DATA_INTEGRATION_PRODUCT,
  DATA_MODELING_PRODUCT,
  DATA_GOVERNANCE_PRODUCT,
  OPERATIONS_PRODUCT,
  MANAGEMENT_PRODUCT,
];

export const ALL_PRODUCT_GROUPS: AllProductGroup[] = [
  { id: "data-modeling", label: DATA_MODELING_PRODUCT_LABEL, products: [DATA_MODELING_PRODUCT] },
  {
    id: "data-integration",
    label: DATA_INTEGRATION_PRODUCT_LABEL,
    products: [DATA_INTEGRATION_PRODUCT],
  },
  {
    id: "data-governance",
    label: DATA_GOVERNANCE_PRODUCT_LABEL,
    products: [DATA_GOVERNANCE_PRODUCT],
  },
  {
    id: "operations",
    label: "运维",
    products: [OPERATIONS_PRODUCT],
  },
  {
    id: "management",
    label: "管理",
    products: [MANAGEMENT_PRODUCT],
  },
];
