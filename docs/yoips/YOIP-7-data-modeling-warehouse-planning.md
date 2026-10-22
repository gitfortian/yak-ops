# YOIP-7: Data Modeling — Warehouse Planning

Status: Discussion

Discussion: Pending proposal PR in `gitfortian/yak-ops`

Decision: Pending explicit maintainer decision

Implementation: N/A until accepted

Release: N/A until released

Related FLIPs: N/A

## Motivation

在 Yak Ops 的一级产品菜单「数据建模」下，新增第一个功能「数仓规划」，用「业务分类 → 数据域 → 业务过程」表达建模工作的业务归属。用户通过左侧树选择节点，在右侧查看详情、维护子级，并查看业务过程关联的逻辑模型。

本次范围是数仓规划的树、详情、创建、编辑、删除、负责人选择与关联资产只读契约。逻辑建模、物理建模、数据标准、指标管理和建模执行不在本提案范围内。

基于本地 `main` 的 `e985604dd6cf1727f7966043635ed53ee6163cfa` 检查到：

- 产品入口由 `app/layout/navigation.ts` 和 `app/router/AppRouter.tsx` 装配，目前没有数据建模入口。
- `AppLayout` 已用 Workspace ID 作为内容的 React key；切换 Workspace 会重新挂载页面。HTTP transport 自动维护 Workspace Header。
- Yak UI 已公开 Button、PageHeader、Table、Dialog、Form、Field、Input、Textarea、Select、Alert 和 Toast，没有公开 Tree。
- 当前产品代码与数据库迁移没有逻辑模型实体或查询接口。不能把尚未接入的资产查询表现为「查询成功且没有关联」。
- Workspace 成员接口返回用户 ID 与角色，现有用户管理查询属于系统级能力。负责人候选需要一个仅返回本 Workspace 有效成员的最小投影。

本文包含用于评审的前端核心代码草案与拟议后端接口；代码块不属于已接入产品的实现，不能作为完整产品编译或运行验收的依据。提案及后续实现 PR 面向 `gitfortian/yak-ops` 的 `main`。

## Public Interfaces

### 产品入口与页面行为

| 项目 | 契约 |
| --- | --- |
| 一级产品菜单 | 数据建模 |
| 二级导航 | 数仓规划 |
| 地址 | `/data-modeling/warehouse-planning` |
| Workspace | 页面、所有节点及负责人候选受当前 Workspace 授权约束 |
| 布局 | 桌面左树约 250px，右侧 `min-width: 0` 自适应；窄屏上下排列 |
| 默认展开 | 业务分类、数据域默认展开；用户可单独折叠 |
| 创建入口 | 页面标题新建业务分类；分类节点新建数据域；数据域节点新建业务过程 |
| 选择联动 | 分类显示数据域表，数据域显示业务过程表，业务过程显示详情和关联资产 |
| 叶节点 | 业务过程没有新增子级按钮 |
| 编辑限制 | 类型和父级不可修改；不提供移动、批量删除或级联删除 |

产品入口同步加入产品启动器、全部产品分组和登录回跳的合法路径集合。通过现有 `AppLayout workspaceScoped` 装配，不建立另一套 Shell、认证或 Workspace 状态。

树节点的新增按钮在鼠标悬浮、键盘焦点进入该行时显示，触屏保持可见。页面私有树使用嵌套列表和共享 Button 提供展开、选择及新增操作；保留原生键盘行为和 `aria-expanded`，不声明未实现完整键盘规范的 ARIA tree。新建成功后展开父级路径并选中新节点。

### 数据与表单契约

所有 ID 在 JSON 中使用字符串，避免 JavaScript 数值精度丢失。

| 字段 | 约束 |
| --- | --- |
| `name` | 必填，去除首尾空白后 1–128 个字符；名称允许正常业务字符，不额外要求全部为汉字 |
| `code` | 必填，1–64 个 ASCII 字符，正则 `^[a-z][a-z0-9_]*$`；非法大小写或空白直接拒绝，不自动改写 |
| `ownerId` | 必填，保存时必须是当前 Workspace 的有效成员，账号有效 |
| `description` | 可空，最多 500 个字符；前端空输入提交 `null` |
| `parentId` | 分类为 `null`；域指向分类，过程指向域；创建后不可改变 |
| `revision` | 创建为 1；更新成功递增；更新和删除必须提交当前值，防止覆盖并发修改 |

**全局唯一按字面定义为：所有 Workspace 的三类数仓规划节点共用一个编码命名空间。** 同一个编码不能同时用于分类、域或过程，跨 Workspace 也不能重复。编码校验只返回可用性，不返回占用方的 Workspace、类型、名称或 ID。该查询不授权跨 Workspace 读取任何节点。

节点保存 `ownerId`，显示名称在读取时通过受限的用户投影获得。已离开 Workspace 或被禁用的负责人仍可在详情中识别，编辑保存时必须重新选择有效成员；本提案不改变现有成员移除流程。

前端拟使用以下类型。节点详情与子级表格直接使用完整树快照，避免分别保存容易相互覆盖的「当前节点详情」副本。

```ts
export type NodeKind = "CATEGORY" | "DOMAIN" | "PROCESS";
export type NodeKey = `${NodeKind}:${string}`;

interface NodeFields {
  id: string;
  name: string;
  code: string;
  ownerId: string;
  ownerName: string | null;
  ownerActive: boolean;
  description: string | null;
  revision: number;
}

export interface CategoryNode extends NodeFields {
  kind: "CATEGORY";
  parentId: null;
  children: DomainNode[];
}

export interface DomainNode extends NodeFields {
  kind: "DOMAIN";
  parentId: string;
  children: ProcessNode[];
}

export interface ProcessNode extends NodeFields {
  kind: "PROCESS";
  parentId: string;
  children: [];
}

export type PlanningNode = CategoryNode | DomainNode | ProcessNode;

export interface NodeInput {
  name: string;
  code: string;
  ownerId: string;
  description: string | null;
}

export type CreateDraft =
  | { mode: "create"; kind: "CATEGORY"; parent: null }
  | { mode: "create"; kind: "DOMAIN"; parent: CategoryNode }
  | { mode: "create"; kind: "PROCESS"; parent: DomainNode };

export type EditorDraft = CreateDraft | {
  mode: "edit";
  node: PlanningNode;
  parent: CategoryNode | DomainNode | null;
};

export interface SavedNode {
  id: string;
  kind: NodeKind;
  revision: number;
}

export interface OwnerOption {
  userId: string;
  displayName: string;
}

export interface Page<T> {
  bizData: T[];
  pagination: { pageNo: number; pageSize: number; total: number; pages: number };
}

export interface LogicalModelSummary {
  id: string;
  name: string;
  code: string;
}

export type AssociatedModels =
  | { availability: "NOT_ENABLED"; page: null }
  | { availability: "AVAILABLE"; page: Page<LogicalModelSummary> };
```

### REST 接口

新接口前缀为 `/api/v1/data-modeling/warehouse-planning`。以下路径均相对此前缀。

所有请求要求登录和有效 Workspace 上下文。Workspace、操作者和审计字段来自现有认证上下文，创建/编辑请求体不接收这些身份字段。拥有当前 Workspace 成员身份且账号有效的用户可查询和维护该 Workspace 的规划节点，符合现有成员协作模式；指定负责人不会赋予额外权限。

| 方法 | 路径 | 输入 | `Result.data` |
| --- | --- | --- | --- |
| GET | `/tree` | 无 | `CategoryNode[]`，完整树快照 |
| GET | `/owners` | 无 | `OwnerOption[]`，当前 Workspace 的有效成员 |
| GET | `/codes/availability` | `code`，编辑时可选 `excludeKind`、`excludeId` | `{ "available": true }` |
| POST | `/categories` | `NodeInput` | `SavedNode` |
| POST | `/categories/{categoryId}/domains` | `NodeInput` | `SavedNode` |
| POST | `/domains/{domainId}/processes` | `NodeInput` | `SavedNode` |
| PUT | `/categories/{id}` | `NodeInput` 加 `revision` | `SavedNode` |
| PUT | `/domains/{id}` | `NodeInput` 加 `revision` | `SavedNode` |
| PUT | `/processes/{id}` | `NodeInput` 加 `revision` | `SavedNode` |
| DELETE | `/categories/{id}` | 查询参数 `revision` | `true` |
| DELETE | `/domains/{id}` | 查询参数 `revision` | `true` |
| DELETE | `/processes/{id}` | 查询参数 `revision` | `true` |
| GET | `/processes/{id}/logical-models` | `pageNo`，`pageSize` | `AssociatedModels` |

`GET /tree` 在一个一致的只读快照中读取三类节点，按 `code`、`id` 稳定排序，不做逐节点 N+1 查询，不把截断结果当成完整树。右侧子级表格在该完整快照上使用 Yak UI 本地分页，默认每页 20 条；关联模型是独立的服务端分页，`pageNo >= 1`、`1 <= pageSize <= 100`。

创建子级时父 ID 只存在于 URL，服务端验证其类型、存在性及 Workspace 归属。编辑接口不接收 `kind`、`parentId` 或父级字段。无权访问的 ID 和不存在的 ID 统一返回资源不存在；不能跨 Workspace 建立父子关系。

编码校验的 `excludeKind` 与 `excludeId` 必须同时传入或同时省略。服务端先授权该编辑对象，再排除它自身。非法编码返回参数错误，合法且被占用返回 `available: false`。可用性查询不预占编码，任何创建和编码更新仍必须通过数据库唯一约束。

创建业务过程示例：

```http
POST /api/v1/data-modeling/warehouse-planning/domains/123456789012345678/processes
Content-Type: application/json
X-Workspace-Id: current-workspace-id

{
  "name": "下单",
  "code": "place_order",
  "ownerId": "123456789012345679",
  "description": "客户提交订单"
}
```

Header 名使用仓库现有 Workspace 常量；页面不手动拼接 Header。成功响应沿用 `Result<T>`，不引入另一套 `success` 或 `items` 包装：

```json
{
  "code": 200,
  "message": "成功",
  "data": { "id": "123456789012345680", "kind": "PROCESS", "revision": 1 }
}
```

更新提交 `{ "name": "下单", "code": "place_order", "ownerId": "...", "description": null, "revision": 1 }`。删除使用 `DELETE /processes/{id}?revision=2`。成功删除是物理删除，不提供隐式恢复或级联删除；编码在同一事务提交后释放。

### 错误与关联资产

拟使用 `19001`–`19005` 作为建模业务错误码，正式实现时检查完整错误码集合。Boot 在现有统一异常映射中显式匹配这些枚举，不能让新增业务错误落入默认 HTTP 500。

| HTTP | 业务码 | 消息与处理 |
| --- | --- | --- |
| 400 | `19001` | 英文编码格式不正确；对应编码 FieldError |
| 409 | `19002` | 英文编码已存在；保留弹窗输入，标记编码字段 |
| 409 | `19003` | 存在关联子节点或资产，无法删除，请先清空 |
| 409 | `19004` | 数据已变更，请刷新后重试；不覆盖用户草稿 |
| 400 | `19005` | 负责人不是当前工作空间有效成员 |
| 400 | 现有 `1001` | 缺少必填字段、长度超限、非法分页或层级参数 |
| 404 | 现有 `10010` | 节点或父节点不存在，或不属于当前 Workspace |
| 401 / 403 | 现有认证/Workspace 错误 | 沿用已有 transport 处理 |

删除受阻响应：

```json
{
  "code": 19003,
  "message": "存在关联子节点或资产，无法删除，请先清空",
  "data": null
}
```

HTTP transport 使用现有红色 Toast 展示受阻消息；页面不解析异常文本判断业务分支，不重复弹出另一条错误通知。数据库异常和 SQL 信息只进入脱敏内部诊断。

当前没有逻辑模型子系统，关联查询先验证过程的 Workspace 归属，再明确返回 `{ "availability": "NOT_ENABLED", "page": null }`，页面显示「逻辑模型尚未开放」。这不是查询失败，也不是关联数量为零。功能开放后，真实查询成功返回 `AVAILABLE` 和现有 `bizData + pagination`；失败维持失败状态并允许重试，不能转换成空集合。

本次不创建逻辑模型、虚构关联记录或临时资产表，不增加没有真实调用者的通用资产插件接口。后续逻辑模型功能在允许建立引用前，必须同时交付实际查询和下文的原子删除保护。

## Proposed Changes

### 后端职责与持久化

新增业务模块 `yak-ops-business-modeling`，拥有规划操作的 Service/Impl、事务和校验；独立调用者使用业务接口，事务标注在托管实现。复用现有 Platform 的有效成员能力、DAO 的 MyBatis/Repository 和 Common 的结果与值类型。Boot 负责 Controller、认证上下文和响应转换；DAO 拥有 Entity、Repository 和新迁移。

该功能不依赖 YakFlow、Core、Task Plugin 或执行模块。Maven 模块注册和现有架构检查需要随正式实现扩展，不建立从业务模块到 Boot 的依赖。

拟新增以下表；最终迁移编号在实现时根据最新迁移序列分配，已有迁移保持不可变。

| 表 | 所有权与约束 |
| --- | --- |
| `yak_ops_modeling_category` | 业务分类；`id`、`workspace_id`、`name`、`code`、`owner_id`、可空描述、`revision` 和审计字段 |
| `yak_ops_modeling_domain` | 数据域；上述字段加不可空 `category_id` |
| `yak_ops_modeling_process` | 业务过程；上述字段加不可空 `domain_id` |
| `yak_ops_modeling_code` | 三类节点共用的编码登记；`code` 唯一，记录 `workspace_id`、`node_kind`、`node_id`；`(node_kind, node_id)` 唯一 |

三类实体保留独立的业务语义和父子关系。编码登记是真实的跨表唯一性所有者，与实体创建、编码修改或删除处于同一数据库事务；任何实体写入失败，编码登记一并回滚。不能仅靠三张表各自的唯一索引宣称全局唯一。

编码使用能精确比较 ASCII 编码的排序规则，数据库唯一索引是最终裁决；并发唯一冲突转换成 `19002`。编码修改需要更新登记和实体，并检查目标实体 `revision`；失败回滚整个事务。登记不作为用户可独立修改的公开资源。

父表建立 `(workspace_id, id)` 唯一键，域的 `(workspace_id, category_id)` 外键引用分类，过程的 `(workspace_id, domain_id)` 外键引用域，均使用 `ON DELETE RESTRICT`，不使用级联删除。父子字段建立查询索引。每个新表与每列都需要有意义的英文 SQL COMMENT；新增 Entity 字段和 Repository/Service 方法按仓库约定补充英文 JavaDoc。

### 删除与并发安全

1. 先认证并验证 Workspace；在事务中锁定目标节点，检查当前 `revision`。
2. 删除分类检查是否存在域；删除域检查是否存在过程和真实逻辑模型引用；删除过程检查真实逻辑模型引用。任一依赖存在则返回 `19003`。
3. 创建子级先锁父节点并确认归属；删除父节点使用相同锁顺序和数据库外键保护。因此「检查后新增」不能留下孤儿节点。
4. 事务中先删除实体，再移除其编码登记；任一步失败全部回滚。不能先清除编码再在另一个事务尝试删除节点。
5. 需要多个节点锁时按分类 → 域 → 过程顺序获取；未来创建模型引用与删除规划节点必须遵循同一顺序，并由真实模型外键或引用所有者的原子协议兜底。

当前没有逻辑模型写入者，不能宣称已经验证了模型并发引用保护。后续模型功能开放的前置验收包含域和过程的直接引用、创建引用与删除节点的竞争；不能仅添加只读列表就允许模型写入引用。

### 前端文件与状态归属

```text
apps/web/app/data-modeling/warehouse-planning/
  index.tsx                 # PageHeader、左树右详情、弹窗装配
  state.ts                  # 快照加载、选择派生、刷新和请求取消
  planning-tree.tsx         # 页面私有的三级树
  planning-details.tsx      # 详情、直接子级 Table
  node-dialog.tsx           # 创建/编辑 Dialog 与字段校验
  delete-dialog.tsx         # 明确删除确认
  process-assets.tsx        # 真实只读资产状态与服务端分页
apps/web/service/data-modeling/warehouse-planning/
  index.ts                  # 仅由上述页面调用的 HTTP 请求
  types.ts                  # 实际领域请求/响应类型
```

页面只保存树快照、选中键、折叠键、弹窗草稿和请求状态。选中节点及子级列表从同一快照派生；不使用全局状态库或 localStorage 保存服务端规划数据。

Service 通过现有 `HttpUtils.*Data` 调用拟议接口，并传递 `AbortSignal`。保持 `App → Service → service/http` 依赖，不在组件中直接调用 fetch，也不另建 transport。现有 transport 的网络异常分支会通知所有 fetch 拒绝；正式实现需让主动取消的 `AbortError` 直接传播而不触发网络 Toast，真实网络、认证与业务失败继续沿用现有处理。示例：

```ts
import HttpUtils from "@/service/http/HttpUtils";
import type { CategoryNode } from "./types";

const API = "/api/v1/data-modeling/warehouse-planning";

export const getPlanningTree = (signal?: AbortSignal) =>
  HttpUtils.getData<CategoryNode[]>(`${API}/tree`, { signal });
```

### 核心代码草案：快照与选择联动

以下分文件代码展示拟议的组件结构与状态算法。弹窗、资产查询的职责在后文定义，需在提案接受后与接口一起实现和验证。

```tsx
// app/data-modeling/warehouse-planning/state.ts
import { useEffect, useRef, useState } from "react";
import { getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import type {
  CategoryNode, DomainNode, NodeKey, PlanningNode,
} from "@/service/data-modeling/warehouse-planning/types";

export const nodeKey = (node: Pick<PlanningNode, "kind" | "id">): NodeKey =>
  `${node.kind}:${node.id}`;

export function findNode(
  nodes: readonly PlanningNode[], key: NodeKey | null,
): PlanningNode | null {
  for (const node of nodes) {
    if (nodeKey(node) === key) return node;
    const child = findNode(node.children, key);
    if (child) return child;
  }
  return null;
}

export function findParent(
  nodes: readonly CategoryNode[], node: PlanningNode,
): CategoryNode | DomainNode | null {
  if (node.kind === "CATEGORY") return null;
  const kind = node.kind === "DOMAIN" ? "CATEGORY" : "DOMAIN";
  const parent = findNode(nodes, `${kind}:${node.parentId}`);
  return parent && parent.kind !== "PROCESS" ? parent : null;
}

export function ancestorKeys(
  nodes: readonly PlanningNode[], key: NodeKey | null,
): NodeKey[] | null {
  for (const node of nodes) {
    if (nodeKey(node) === key) return [];
    const path = ancestorKeys(node.children, key);
    if (path) return [nodeKey(node), ...path];
  }
  return null;
}

export function usePlanning() {
  const [snapshot, setSnapshot] = useState<{
    roots: CategoryNode[];
    selectedKey: NodeKey | null;
  }>({ roots: [], selectedKey: null });
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [version, setVersion] = useState(0);
  const preferredKey = useRef<NodeKey | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setFailed(false);
    void getPlanningTree(controller.signal)
      .then((roots) => {
        if (!active) return;
        const preferred = preferredKey.current;
        preferredKey.current = null;
        setSnapshot((previous) => {
          const requested = preferred ?? previous.selectedKey;
          return {
            roots,
            selectedKey: findNode(roots, requested)
              ? requested
              : roots[0] ? nodeKey(roots[0]) : null,
          };
        });
      })
      .catch(() => {
        if (active) setFailed(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [version]);

  return {
    ...snapshot,
    loading,
    failed,
    selected: findNode(snapshot.roots, snapshot.selectedKey),
    select: (selectedKey: NodeKey) =>
      setSnapshot((previous) => ({ ...previous, selectedKey })),
    refresh: (preferred?: NodeKey) => {
      if (preferred) preferredKey.current = preferred;
      setVersion((value) => value + 1);
    },
  };
}
```

刷新时保留快照供状态连续展示，但 loading 或失败期间禁止基于旧快照执行写操作。请求取消和 active 检查阻止旧 Workspace、已卸载页面或前一次刷新覆盖新状态。保存成功后的刷新失败只显示「加载失败」和重试入口，不把已经成功的保存报告为失败，也不自动重复写请求。

### 核心代码草案：私有树

```tsx
// app/data-modeling/warehouse-planning/planning-tree.tsx
import { Button } from "@yak-ops/yak-ui";
import { ChevronDown, ChevronRight, FileText, Folder, Plus } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import type {
  CategoryNode, DomainNode, NodeKey, PlanningNode,
} from "@/service/data-modeling/warehouse-planning/types";
import { ancestorKeys, nodeKey } from "./state";

interface Props {
  nodes: readonly CategoryNode[];
  selectedKey: NodeKey | null;
  disabled: boolean;
  onSelect: (key: NodeKey) => void;
  onCreateChild: (parent: CategoryNode | DomainNode) => void;
}

export default function PlanningTree(props: Props) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<NodeKey>>(() => new Set());

  useEffect(() => {
    const ancestors = new Set(ancestorKeys(props.nodes, props.selectedKey) ?? []);
    setCollapsed((previous) => new Set([...previous].filter((key) => !ancestors.has(key))));
  }, [props.nodes, props.selectedKey]);

  const toggle = (key: NodeKey) => setCollapsed((previous) => {
    const next = new Set(previous);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    return next;
  });

  const renderNodes = (nodes: readonly PlanningNode[]): ReactNode => (
    <ul className="space-y-1">
      {nodes.map((node) => {
        const key = nodeKey(node);
        const expanded = !collapsed.has(key);
        const childLabel = node.kind === "CATEGORY" ? "数据域" : "业务过程";
        return (
          <li key={key}>
            <div className="group/row flex min-w-0 items-center gap-1">
              {node.children.length > 0 ? (
                <Button size="small" variant="ghost" disabled={props.disabled}
                  aria-label={`${expanded ? "折叠" : "展开"}${node.name}`}
                  aria-expanded={expanded} onClick={() => toggle(key)}>
                  {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                </Button>
              ) : <span className="w-7 shrink-0" />}
              <Button size="small" disabled={props.disabled}
                variant={props.selectedKey === key ? "secondary" : "ghost"}
                className="min-w-0 flex-1 justify-start"
                aria-current={props.selectedKey === key ? true : undefined}
                onClick={() => props.onSelect(key)}>
                {node.kind === "PROCESS" ? <FileText size={14} /> : <Folder size={14} />}
                <span className="truncate">{node.name}</span>
              </Button>
              {node.kind !== "PROCESS" && (
                <Button size="small" variant="ghost" disabled={props.disabled}
                  className="opacity-100 md:opacity-0 md:group-hover/row:opacity-100 md:group-focus-within/row:opacity-100"
                  aria-label={`在${node.name}下新建${childLabel}`}
                  onClick={() => props.onCreateChild(node)}>
                  <Plus size={14} />
                </Button>
              )}
            </div>
            {expanded && node.children.length > 0 && (
              <div className="ml-5">{renderNodes(node.children)}</div>
            )}
          </li>
        );
      })}
    </ul>
  );

  return <nav aria-label="数仓规划目录">{renderNodes(props.nodes)}</nav>;
}
```

### 核心代码草案：详情与页面装配

```tsx
// app/data-modeling/warehouse-planning/planning-details.tsx
import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import type {
  CategoryNode, DomainNode, PlanningNode,
} from "@/service/data-modeling/warehouse-planning/types";
import ProcessAssets from "./process-assets";
import { nodeKey } from "./state";

interface Props {
  node: PlanningNode;
  parent: CategoryNode | DomainNode | null;
  onSelect: (node: PlanningNode) => void;
  onCreateChild: (parent: CategoryNode | DomainNode) => void;
  onEdit: () => void;
  onDelete: () => void;
}

export default function PlanningDetails(props: Props) {
  const { node, parent } = props;
  const columns: TableColumns<PlanningNode> = [
    { title: "名称", key: "name", render: (_, row) => (
      <Button size="small" variant="ghost" onClick={() => props.onSelect(row)}>
        {row.name}
      </Button>
    ) },
    { title: "英文编码", dataIndex: "code" },
    { title: "负责人", key: "owner", render: (_, row) => row.ownerName ?? row.ownerId },
    { title: "描述", dataIndex: "description", ellipsis: true },
  ];

  return (
    <section className="min-w-0 space-y-4">
      <div className="rounded border border-[var(--yak-components-panel-border)] p-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-medium">{node.name}</h2>
          <div className="flex gap-2">
            <Button size="small" onClick={props.onEdit}>编辑</Button>
            <Button size="small" variant="danger" onClick={props.onDelete}>删除</Button>
          </div>
        </div>
        <dl className="mt-3 grid grid-cols-[104px_minmax(0,1fr)] gap-2 text-sm">
          <dt>英文编码</dt><dd>{node.code}</dd>
          {parent && <><dt>{node.kind === "PROCESS" ? "所属数据域" : "所属业务分类"}</dt><dd>{parent.name}</dd></>}
          <dt>负责人</dt><dd>{node.ownerName ?? node.ownerId}{!node.ownerActive && "（已不可选）"}</dd>
          <dt>描述</dt><dd className="whitespace-pre-wrap break-words">{node.description || "—"}</dd>
        </dl>
      </div>
      {node.kind === "PROCESS" ? <ProcessAssets key={node.id} processId={node.id} /> : (
        <div>
          <div className="mb-3 flex items-center justify-between">
            <h3>{node.kind === "CATEGORY" ? "数据域" : "业务过程"}</h3>
            <Button size="small" variant="primary" onClick={() => props.onCreateChild(node)}>
              {node.kind === "CATEGORY" ? "新建数据域" : "新建业务过程"}
            </Button>
          </div>
          <Table<PlanningNode> key={nodeKey(node)} rowKey={nodeKey}
            columns={columns} dataSource={node.children} pagination={{ pageSize: 20 }}
            size="small" emptyText="暂无数据" />
        </div>
      )}
    </section>
  );
}
```

```tsx
// app/data-modeling/warehouse-planning/index.tsx
import { Alert, Button, PageHeader, Spinner } from "@yak-ops/yak-ui";
import { useState } from "react";
import type {
  CategoryNode, DomainNode, EditorDraft, PlanningNode, SavedNode,
} from "@/service/data-modeling/warehouse-planning/types";
import DeleteDialog from "./delete-dialog";
import NodeDialog from "./node-dialog";
import PlanningDetails from "./planning-details";
import PlanningTree from "./planning-tree";
import { findParent, nodeKey, usePlanning } from "./state";

export default function WarehousePlanningPage() {
  const planning = usePlanning();
  const [draft, setDraft] = useState<EditorDraft | null>(null);
  const [deleting, setDeleting] = useState<PlanningNode | null>(null);
  const selected = planning.selected;
  const disabled = planning.loading || planning.failed;

  const createChild = (parent: CategoryNode | DomainNode) => {
    setDraft(parent.kind === "CATEGORY"
      ? { mode: "create", kind: "DOMAIN", parent }
      : { mode: "create", kind: "PROCESS", parent });
  };
  const saved = (node: SavedNode) => {
    setDraft(null);
    planning.refresh(nodeKey(node));
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader title="数仓规划" extra={
        <Button size="small" variant="primary" disabled={disabled}
          onClick={() => setDraft({ mode: "create", kind: "CATEGORY", parent: null })}>
          新建业务分类
        </Button>
      } />
      {planning.failed && <Alert>
        数仓规划加载失败
        <Button size="small" onClick={() => planning.refresh()}>重试</Button>
      </Alert>}
      <div aria-busy={planning.loading}
        className="grid min-h-0 flex-1 grid-cols-1 gap-4 md:grid-cols-[250px_minmax(0,1fr)]">
        <aside className="min-h-0 overflow-auto border-r border-[var(--yak-components-panel-border)] pr-3">
          <PlanningTree nodes={planning.roots} selectedKey={planning.selectedKey}
            disabled={disabled} onSelect={planning.select} onCreateChild={createChild} />
        </aside>
        <main className="min-h-0 min-w-0 overflow-auto">
          {planning.loading ? <Spinner /> : planning.failed ? null : selected ? (
            <PlanningDetails node={selected} parent={findParent(planning.roots, selected)}
              onSelect={(node) => planning.select(nodeKey(node))}
              onCreateChild={createChild} onDelete={() => setDeleting(selected)}
              onEdit={() => setDraft({ mode: "edit", node: selected,
                parent: findParent(planning.roots, selected) })} />
          ) : <p className="p-4 text-sm">暂无业务分类</p>}
        </main>
      </div>
      {draft && <NodeDialog draft={draft} onClose={() => setDraft(null)} onSaved={saved} />}
      {deleting && <DeleteDialog node={deleting} onClose={() => setDeleting(null)}
        onDeleted={() => {
          const parent = findParent(planning.roots, deleting);
          setDeleting(null);
          planning.refresh(parent ? nodeKey(parent) : undefined);
        }} />}
    </div>
  );
}
```

`NodeDialog` 的 `draft` 固定创建时的父对象；不能随着树的当前选择变化而重绑父级。`DeleteDialog` 固定待删除对象和 `revision`，用户确认后只提交一次删除请求。两个 Dialog 都由显式按钮关闭，提交期间禁止重复确认和关闭；失败保持原输入和目标。

### 表单弹窗与资产模块的实现契约

- 使用 Yak UI `Dialog`、`DialogContent`、`DialogTitle` 和 `Form/Field`，标签列为 104px，控件采用 `small + outlined`。原生表单提交支持 Enter，字段使用关联的 Label、必填属性和 FieldError。
- 创建业务过程时，所属数据域的 Select 为禁用状态，显示草稿中的域名称；提交的父 ID 来自该草稿。域表单同样固定分类归属，编辑时不开放迁移。
- 负责人通过 `/owners` 加载；候选值为用户 ID，显示名称为 `displayName`，不复用系统级用户搜索扩大可见范围。加载失败提供重试并阻止提交，不能解释为空成员列表。
- 客户端先校验必填、长度和编码正则。合法编码在失焦时查询可用性；输入变化或 Dialog 关闭取消旧校验，响应只有在编码与编辑对象仍匹配时才能更新 FieldError。
- 可用性查询是提示，保存请求仍由服务端做最终唯一性裁决；`19002` 保留草稿并标记 code，`19004` 保留草稿并要求显式刷新，不自动覆盖服务器内容。
- 保存成功先关闭表单并展示成功 Toast，再触发树刷新；刷新函数不与保存共用一个会把刷新错误解释为写入失败的 catch。
- 创建/编辑/删除请求不做自动重试；按钮使用 loading，并以提交锁阻止同一 Dialog 的重复提交。
- `ProcessAssets` 在过程 ID、页码变化时取消旧读取；显示 loading、失败重试、`NOT_ENABLED` 或真实 Table 四种状态。Table 只包含逻辑模型名称与英文编码，不增加创建或删除关联操作，不链接到尚未存在的页面。

## Compatibility, Deprecation, and Migration Plan

- 新增产品入口和 REST 接口，不改变数据集成、Task、Schedule、插件 SPI、任务定义 JSON 或 Checkpoint 格式。
- 新表由正向 Flyway 迁移创建，已有业务数据无需回填，现有迁移不修改。不添加演示分类或假资产，不执行外部数据库 DDL。
- 新增全局编码命名空间仅约束本提案的三类规划节点，不追溯约束现有数据源或 Task 编码。
- 前后端应一同交付。新前端遇到缺少规划接口的旧后端时显示请求失败，不回落到 mock、localStorage 或伪造的成功状态。
- 逻辑模型能力开放时，需补充其真实持久化、授权、引用和删除保护的独立设计与验收；`NOT_ENABLED` 响应不能继续用于掩盖真实查询失败。

## Test Plan

本文为提案与代码草案，下面是实现验收计划，不表示已经执行或通过。

| 范围 | 需要证明的行为 |
| --- | --- |
| Service 单测 | 三层合法创建、必填/长度/正则、父类型固定、负责人有效性、版本冲突、无权 ID 与不存在 ID 的一致行为 |
| 真实数据库测试 | 三表/跨 Workspace 的编码竞争只有一次成功；失败事务不遗留编码占用；修改编码与实体原子提交；复合外键拒绝跨 Workspace 父子关系 |
| 并发删除 | 删除分类与创建域、删除域与创建过程竞争，没有孤儿或误删；实体删除与编码释放一致；已存在子级返回 `19003` |
| HTTP E2E | 真实登录、Workspace 隔离、候选授权、错误 HTTP/业务码、revision、完整树与 `Result/PagingData` 契约 |
| 前端静态检查 | `npm run check` 和 `npm run build`；沿用格式、Lint、类型、架构与已有契约检查 |
| 浏览器 | 菜单、三层默认展开、折叠、树/表互选、新增父级固定、字段错误、删除红色 Toast、空状态和失败重试 |
| 交互竞争 | 连续切换节点与 Workspace、旧请求取消、重复提交、保存成功后读取失败、删除当前节点后的选择回退 |
| 可访问性 | Tab/Enter/Space 操作树和 Dialog、焦点恢复、仅键盘显示新增按钮、窄屏和减少动画 |
| 关联资产 | 当前明确显示未开放；以后开放时验证真实分页、读失败与空结果区分，以及引用/删除并发保护 |

数据库 IT 使用仓库现有 `scripts/tests/run.sh` 与套件清单，普通 Maven verify 不启动 Docker；新增 HTTP 产品测试归 `yak-ops-e2e`。浏览器验收扩展稳定业务场景，不增加临时每功能脚本或在 Playwright 中等待真实 JDBC/CDC。

## Rejected Alternatives

- **只做扁平列表：** 无法直接表达三层业务归属和父级上下文。
- **引入新 UI 库或提前扩展公共 Tree：** 当前只有本页面需要此树；先使用页面私有模块与现有 Yak UI 公共组件，避免新增无实际复用者的公共契约。
- **只在前端或保存前查询编码：** 无法阻止并发重复，也无法保证三张独立表之间的唯一性。
- **按每表或每 Workspace 放宽唯一性：** 不满足本次明确的全局编码要求。
- **只检查 children 后删除：** 存在检查与写入之间的竞争，不能代替事务和数据库约束。
- **级联删除或自动解绑资产：** 会隐式删除业务规划或破坏模型归属，违背删除保护要求。
- **用占位模型表、假引用接口或永远空的查询表达关联资产：** 会误导用户判断依赖是否存在，并引入没有真实模型所有者的结构。
- **页面维护第二套详情状态或 HTTP transport：** 增加树、详情、Workspace 和错误状态不一致的机会。
