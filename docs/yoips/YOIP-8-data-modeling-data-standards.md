# YOIP-8: Data Modeling — Data Standards

Status: Accepted

Discussion: https://github.com/gitfortian/yak-ops/pull/5

Decision: [VOTE] +1 Accepted — https://github.com/gitfortian/yak-ops/pull/5#issuecomment-6334466443

Implementation: Planned — Data Standards

Release: Proposed 1.5.0, after warehouse planning 1.4.0

Related FLIPs: N/A

## Motivation

在「数据建模」一级菜单下增加「数据标准」，维护字段的名称、编码、逻辑数据类型、长度或精度、非空约束、安全等级、负责人和业务口径。标准分类采用多级目录，右侧提供可搜索、分页及逐行编辑和删除的标准列表。

本次包括分类维护、字段标准维护、Workspace 授权、编码唯一性、目录删除保护与引用检查的明确状态。逻辑模型、自动属性继承、标准版本发布、审批、导入导出、目录移动和批量操作不在本次范围内。

### Current code and prerequisite

- 提案基于 `origin/main` 的 `b7a538ef0ea9752e198a92e4deee1cd89a587923`，只增加本文档。
- 已接受的 [YOIP-7](YOIP-7-data-modeling-warehouse-planning.md) 定义数仓规划；其实现 [PR #4](https://github.com/gitfortian/yak-ops/pull/4) 的检查基点为 `b29706144c3b7b63b50792ad5e05547516bd9908`。数据标准实现以该实现合并后为前提，扩展现有 Modeling business 模块。
- 已检查现有导航、路由、数仓规划页面及领域请求、Workspace 授权、负责人投影、分页封装和 Yak UI 公共类型。Yak UI 公开 Table、Dialog、Form、Field、Input、Textarea、Select、Button 等组件，没有公开 Tree；目录采用与数仓规划一致的页面私有嵌套列表，不引入组件库。
- `AppLayout` 按 Workspace 隔离页面实例，HTTP transport 维护 Workspace Header；领域页面不拼接 Header 或解析 transport 错误。
- 当前没有逻辑模型及字段引用实体，不能把预留引用检查显示为已经查询到零条引用。
- 维护者已在本次需求讨论中明确选择：负责人使用下拉框，默认当前用户，可选择当前 Workspace 的有效成员。

以下代码是用于评审的前端核心草案，不是已经接入产品的实现。本文及后续 PR 仅面向 `gitfortian/yak-ops`；不提交至 upstream。

## Public Interfaces

### Product behavior

| 项目 | 契约 |
| --- | --- |
| 菜单 | 数据建模 → 数据标准，与数仓规划并列 |
| 路径 | `/data-modeling/data-standards`，加入导航及登录回跳合法路径 |
| 布局 | 桌面左目录约 250px、右侧自适应；窄屏上下排列；表格可横向滚动 |
| 默认目录 | 每个 Workspace 首次显式初始化时创建「公共标准」「业务标准」两个根目录 |
| 目录层级 | 多级，根目录为第 1 级，最多 32 级；默认展开已有目录 |
| 目录操作 | 工具栏新建根目录；节点操作提供新建同级、新建子级、重命名、删除；叶节点也可新建子级 |
| 选中分类 | 显示直属字段标准；不隐式汇总子分类；清空全局搜索并重置到第 1 页 |
| 全局搜索 | 非空关键词按中文名称或英文编码搜索当前 Workspace 的全部分类；保留此前选中分类，清空搜索后恢复它 |
| 搜索结果 | 增加「所属分类」路径列，避免跨分类结果无法辨识；行编辑始终锁定记录实际分类 |
| 新建标准 | 必须有有效选中分类；即使正在全局搜索，也使用该选中分类，弹窗明确显示分类路径 |
| 表格 | 中文名称、英文编码、数据类型、长度/精度、安全等级、负责人、更新时间、操作；服务端分页 |
| 行操作 | 最右侧固定「操作」列，编辑为主题蓝文字按钮，删除为灰色文字、红色悬浮；保留确认弹窗 |
| 保存与删除 | 保留查询、父级选择和分页上下文，刷新列表；删除最后一条后回退到仍存在的最后一页 |
| 空目录 | 所有目录均可在无依赖时删除；没有选中目录时禁用新建标准，仍可新建根目录和全局搜索 |

搜索使用显式提交或清空，提交后回到第 1 页。列表请求取消或被新查询替代时，过期响应不能覆盖新结果；正常取消不显示网络失败。切换 Workspace 卸载目录、搜索、分页、弹窗和未完成请求。

编辑和删除失败保留弹窗及输入。revision 冲突提示刷新后重试，不静默覆盖；目录被其他用户删除时，不在旧分类下继续提交新标准。

### Fields and validation

所有 JSON ID 均为字符串。名称不要求全部为汉字；允许常见业务字符。

| 字段 | 创建与编辑契约 |
| --- | --- |
| `categoryId` | 创建由 URL 指定；弹窗只读显示分类路径；更新不能修改分类 |
| `cnName` | 必填，去除首尾空白后 1–128 个字符 |
| `enCode` | 必填，1–64 个 ASCII 字符，`^[a-z][a-z0-9_]*$`；拒绝空白和大写，不自动转小写 |
| `dataType` | 必填，`STRING / BIGINT / DECIMAL / DATE / TIMESTAMP` |
| `lengthPrecision` | 可空；STRING 为 1–2147483647 的十进制整数；DECIMAL 为 `p,s`，1 ≤ p ≤ 38、0 ≤ s ≤ p；其他类型必须为空 |
| `isNotNull` | 必填布尔值，新建默认 `false`；表单单选「是 / 否」 |
| `securityLevel` | 必填，`PUBLIC / INTERNAL / SENSITIVE / CONFIDENTIAL`，分别显示公开/内部/敏感/机密；默认 INTERNAL |
| `ownerId` | 必填，默认当前用户；保存时为当前 Workspace 有效成员且账号有效 |
| `businessCaliber` | 可空，多行文本，最多 2000 字符；空输入提交 null |
| `revision` | 创建为 1；修改成功递增；更新和删除必须提交当前值 |

`lengthPrecision` 是逻辑标准约束，不承诺任何数据库已经创建对应字段。类型切换到 BIGINT、DATE 或 TIMESTAMP 时清空并禁用该输入；在 STRING 和 DECIMAL 之间切换时清空旧值，避免把长度当作精度提交。DECIMAL 可以不填，填写时必须是完整的 `18,2` 形式。

负责人已失效时，历史记录仍显示其身份或不可选状态；编辑保存前必须选择有效成员。候选只返回 `userId` 和 `displayName`，不暴露邮箱、手机或认证数据。

**英文编码在 `standard_field` 的所有 Workspace 记录中唯一。** 不与数仓规划的分类/域/过程编码共用登记表；例如标准字段与业务过程可以使用相同编码。可用性检查只返回布尔值，不披露占用者身份；写入以数据库唯一约束为最终裁决。

### REST contracts

前缀：`/api/v1/data-modeling/data-standards`。

所有接口沿现有认证和 Workspace 授权链执行；Workspace 来自受信上下文，不从 body 或查询参数接受。跨 Workspace ID 与不存在 ID 均返回 404；未获当前 Workspace 授权返回 403。GET 均无写入副作用。

| Method | Path | 请求 | 结果 |
| --- | --- | --- | --- |
| POST | `/catalog/initialize` | 无 body | 幂等初始化并返回完整分类树；重复调用不重建已删除的默认目录 |
| GET | `/categories/tree` | 无 | `StandardCategory[]`；一致快照 |
| POST | `/categories` | `{ parentId: string \| null, name }` | `{ id, revision: 1 }` |
| PUT | `/categories/{id}` | `{ name, revision }` | `{ id, revision }`；仅重命名 |
| DELETE | `/categories/{id}?revision=1` | 无 body | `true`；禁止删除含后代分类或标准的目录 |
| GET | `/fields` | `categoryId` 或非空 `keyword`，`pageNo=1&pageSize=20` | `PagingData<StandardField>` |
| GET | `/fields/{id}` | 无 | 标准详情及当前分类路径、revision |
| POST | `/categories/{id}/fields` | `StandardInput` | `{ id, revision: 1 }` |
| PUT | `/fields/{id}` | `StandardInput & { revision }` | `{ id, revision }`；分类固定 |
| DELETE | `/fields/{id}?revision=1` | 无 body | `true`；真实引用存在时返回 409 |
| GET | `/fields/codes/availability` | `enCode`，可选 `excludeId` | `{ available: boolean }`；对应 `checkCodeUnique` |
| GET | `/fields/{id}/references` | 无 | `StandardReferences`；对应 `checkStandardReferenced` |
| GET | `/owners` | 无 | 当前 Workspace 的有效成员最小投影 |

`GET /fields` 的分类与关键词互斥：分类查询必须提供有效 categoryId；关键词查询忽略左树选择并省略 categoryId。两者都提供或都不提供返回 400。关键词去除首尾空白后为 1–128 字符；按名称或编码包含匹配，英文搜索不区分大小写；`%`、`_` 和转义符按普通字符转义后绑定参数，不允许成为用户 SQL 通配符。

分页要求 pageNo ≥ 1、1 ≤ pageSize ≤ 100；固定按 `update_time DESC, id DESC` 排序，不接受任意 SQL 排序字段。越界页返回空记录及真实总数，前端按总数回退一次；count 和当前页在同一只读快照中产生。

可用性请求携带 excludeId 时，先验证被编辑标准属于当前 Workspace。检查不预留编码，失败时不把「无法检查」改写为「可用」。提交不依赖预检查成功，仍处理并发写入返回的唯一冲突。

成功沿用 `Result<T>`，`code=200`；HTTP 分页沿用 `bizData + pagination`，内部使用已有 `PageData<T>`，不新增另一种分页外壳。

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "bizData": [{
      "id": "standard_1",
      "categoryId": "category_trade",
      "categoryPath": "业务标准 / 交易类",
      "cnName": "支付金额",
      "enCode": "pay_amt",
      "dataType": "DECIMAL",
      "lengthPrecision": "18,2",
      "isNotNull": false,
      "securityLevel": "INTERNAL",
      "ownerId": "user_1",
      "ownerName": "张三",
      "ownerActive": true,
      "businessCaliber": "订单实际支付金额，不含退款金额",
      "updateTime": "2026-10-22T14:00:00",
      "revision": 1
    }],
    "pagination": { "pageNo": 1, "pageSize": 20, "total": 1, "pages": 1 }
  }
}
```

时间值沿现有平台审计时间与序列化约定，不另建时区规则。示例 ID 和记录仅用于说明格式。

| HTTP | 错误 | 前端行为 |
| --- | --- | --- |
| 400 | 参数、类型、精度或负责人无效 | 本地校验或已知业务错误映射到对应 FieldError |
| 403 | Workspace 无权访问 | 复用现有授权失败处理 |
| 404 | 记录或固定分类不存在/不可见 | 关闭过期选择前保留草稿，提示刷新 |
| 409 | 标准编码重复 | 英文编码 FieldError；服务端返回本地化安全文案 |
| 409 | revision 过期 | 保留草稿，明确要求刷新后重试 |
| 409 | 目录存在依赖 | 红色 Toast「存在子分类或字段标准，无法删除」 |
| 409 | 标准被真实引用 | 红色 Toast「该标准已被资产引用，无法删除」 |
| 500 | 未预期异常 | 统一安全提示；日志保留异常类型、原因和必要标识，不返回内部 SQL 或业务口径 |

实现时在 owning enum 中分配不与现有 ModelingErrorCode 冲突的独立错误码；领域请求模块识别具体业务字段，页面不依赖异常文案判断分支。

### Logical-model reference state

```ts
export type StandardReferences =
  | { availability: "NOT_ENABLED"; referenced: null; count: null }
  | { availability: "AVAILABLE"; referenced: boolean; count: number };
```

本次 `checkStandardReferenced` 先校验字段所属 Workspace，再返回 NOT_ENABLED。它不会伪造 `referenced: false`，页面也不显示「已验证无引用」。当前没有逻辑模型引用写入能力，因此在目录和 revision 检查通过后允许删除字段。

将来接入逻辑模型时，引用写入、更新及删除必须与标准生命周期协同，并通过同 Workspace 复合外键 `ON DELETE RESTRICT` 和事务检查保护。必须先实现真实读取与删除保护，再启用任何引用写入；预检查接口不能替代 DELETE 的最终校验。本提案不新增无调用者的引用表、插件接口或占位适配器。

## Proposed Changes

### Ownership and component structure

```text
app/data-modeling/data-standards/
  index.tsx                 # selection, search, pagination and dialog targets
  category-tree.tsx         # private nested directory navigation
  category-dialog.tsx       # create sibling/child/root and rename
  standard-table.tsx        # shared Table columns and row callbacks
  standard-dialog.tsx       # fixed category, fields and validation
  delete-dialog.tsx         # confirmation, busy and conflict state
  state.ts                  # cancellable tree/list reads and query transitions
service/data-modeling/data-standards/
  index.ts                  # transport calls and business-error classification
  types.ts                  # request, row, tree and reference contracts
```

后端在已有 `yak-ops-business-modeling` 中按 `standard` 功能所有权组织标准业务。业务 Interface 与 Impl 承载独立 HTTP 调用契约、授权、输入校验、事务、revision 和删除保护；DAO 承载 Entity、Repository、Mapper 与迁移；Boot 只负责 HTTP 装配。复用 Workspace 和用户查询能力，不从业务反向依赖 Boot，不影响 Core、Runtime、Task、插件或 checkpoint。

不将数仓规划的三级树改造成带类型分支的通用目录框架。数据标准拥有多级分类及分页字段的独立状态，复用 Yak UI 视觉组件和既有成员投影约定。只有出现实际共享调用时才提取公共逻辑。

页面布局核心保持为受控的左树右表，目录、列表和弹窗的真实数据由页面状态提供：

```tsx
import type { ReactNode } from "react";
import { Button, Form, Input, PageHeader } from "@yak-ops/yak-ui";

export function DataStandardsLayout(props: {
  tree: ReactNode;
  table: ReactNode;
  searchText: string;
  canCreate: boolean;
  onSearchText: (value: string) => void;
  onSearch: () => void;
  onCreate: () => void;
}) {
  return <div className="flex h-full min-h-0 flex-col">
    <PageHeader title="数据标准" bordered className="bg-white px-6 max-md:px-4" />
    <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 px-6 py-5 max-md:px-4 md:grid-cols-[250px_minmax(0,1fr)]">
      <aside aria-label="标准分类" className="min-h-0 overflow-auto">{props.tree}</aside>
      <section aria-label="字段标准" className="flex min-h-0 min-w-0 flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Form className="flex items-center gap-2" onSubmit={(event) => {
            event.preventDefault(); props.onSearch();
          }}>
            <Input size="small" variant="outlined" value={props.searchText}
              aria-label="搜索全部分类中的标准" placeholder="中文名称或英文编码"
              onChange={(event) => props.onSearchText(event.target.value)} />
            <Button size="small" type="submit">搜索</Button>
          </Form>
          <Button size="small" variant="primary" disabled={!props.canCreate}
            onClick={props.onCreate}>新建标准</Button>
        </div>
        <div className="min-h-0 min-w-0 flex-1 overflow-auto">{props.table}</div>
      </section>
    </div>
  </div>;
}
```

### Core frontend types

```ts
export type DataType = "STRING" | "BIGINT" | "DECIMAL" | "DATE" | "TIMESTAMP";
export type SecurityLevel = "PUBLIC" | "INTERNAL" | "SENSITIVE" | "CONFIDENTIAL";

export interface StandardCategory {
  id: string;
  parentId: string | null;
  name: string;
  revision: number;
  children: StandardCategory[];
}

export interface StandardInput {
  cnName: string;
  enCode: string;
  dataType: DataType;
  lengthPrecision: string | null;
  isNotNull: boolean;
  securityLevel: SecurityLevel;
  ownerId: string;
  businessCaliber: string | null;
}

export interface StandardField extends StandardInput {
  id: string;
  categoryId: string;
  categoryPath: string;
  ownerName: string | null;
  ownerActive: boolean;
  updateTime: string;
  revision: number;
}

export interface StandardPage {
  bizData: StandardField[];
  pagination: { pageNo: number; pageSize: number; total: number; pages: number };
}

export type StandardQuery =
  | { scope: "CATEGORY"; categoryId: string; pageNo: number; pageSize: number }
  | { scope: "ALL"; keyword: string; pageNo: number; pageSize: number };

export type StandardDraft =
  | { mode: "create"; category: { id: string; path: string } }
  | { mode: "edit"; standard: StandardField };

export interface OwnerOption { userId: string; displayName: string }
```

### Domain request seam

```ts
import HttpUtils from "@/service/http/HttpUtils";
import type { StandardDraft, StandardInput, StandardPage, StandardQuery,
  StandardReferences } from "./types";

const API = "/api/v1/data-modeling/data-standards";

export function getStandards(query: StandardQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({
    pageNo: String(query.pageNo), pageSize: String(query.pageSize),
  });
  if (query.scope === "CATEGORY") params.set("categoryId", query.categoryId);
  else params.set("keyword", query.keyword);
  return HttpUtils.getData<StandardPage>(`${API}/fields?${params}`, { signal });
}

export function checkCodeUnique(enCode: string, excludeId?: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ enCode });
  if (excludeId) params.set("excludeId", excludeId);
  return HttpUtils.getData<{ available: boolean }>(
    `${API}/fields/codes/availability?${params}`, { signal },
  );
}

export const checkStandardReferenced = (id: string, signal?: AbortSignal) =>
  HttpUtils.getData<StandardReferences>(
    `${API}/fields/${encodeURIComponent(id)}/references`, { signal },
  );

export function saveStandard(draft: StandardDraft, input: StandardInput) {
  if (draft.mode === "edit") {
    return HttpUtils.putData<{ id: string; revision: number }>(
      `${API}/fields/${encodeURIComponent(draft.standard.id)}`,
      { ...input, revision: draft.standard.revision },
    );
  }
  return HttpUtils.postData<{ id: string; revision: number }>(
    `${API}/categories/${encodeURIComponent(draft.category.id)}/fields`, input,
  );
}
```

分类初始化、树读取、负责人、分类写入及 DELETE 同样只从该领域请求模块导出。此草案展示关键调用，不声称其余函数已实现。

### Query and tree/list linkage

```tsx
import { useEffect, useState } from "react";
import { getStandards } from "@/service/data-modeling/data-standards";
import type { StandardPage, StandardQuery } from
  "@/service/data-modeling/data-standards/types";

export function useStandardPage(query: StandardQuery | null, refreshVersion: number) {
  const [result, setResult] = useState<StandardPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setResult(null);
    setFailed(false);
    setLoading(query !== null);
    if (query) {
      void getStandards(query, controller.signal)
        .then((page) => { if (active) setResult(page); })
        .catch(() => { if (active) setFailed(true); })
        .finally(() => { if (active) setLoading(false); });
    }
    return () => { active = false; controller.abort(); };
  }, [query, refreshVersion]);
  return { result, loading, failed };
}
```

页面通过 useMemo 从 selectedCategoryId、已提交 keyword、pageNo、pageSize 派生稳定 query；不在每次 render 中创建无条件的新查询对象。树和列表分别拥有可取消读取，但不保存重复的选中分类对象；路径从当前树按 ID 派生。

```tsx
// Page-owned event transitions; mounted inside the existing Workspace-keyed layout.
const selectCategory = (id: string) => {
  setSelectedCategoryId(id);
  setSearchText("");
  setKeyword("");
  setPageNo(1);
};
const submitSearch = () => {
  setKeyword(searchText.trim());
  setPageNo(1);
};
const editStandard = (row: StandardField) =>
  setDraft({ mode: "edit", standard: row });
const saved = () => {
  setDraft(null);
  setRefreshVersion((value) => value + 1);
};
```

最后一段为页面事件草案，setters 由页面实际 useState 提供。默认目录初始化成功后选择仍存在的原分类，否则首个根分类；初始化失败显示重试并阻止写操作。目录新增选择新节点、展开祖先；重命名按 ID 保留选择。删除当前空目录后选择父级或首个剩余根目录。

### Table core

```tsx
import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import type { StandardField, StandardPage } from
  "@/service/data-modeling/data-standards/types";

const SECURITY_LABEL = {
  PUBLIC: "公开", INTERNAL: "内部", SENSITIVE: "敏感", CONFIDENTIAL: "机密",
};

export function StandardTable(props: {
  page: StandardPage | null;
  pageNo: number;
  pageSize: number;
  loading: boolean;
  searching: boolean;
  onEdit: (row: StandardField) => void;
  onDelete: (row: StandardField) => void;
  onPageChange: (page: number, pageSize: number) => void;
}) {
  const columns: TableColumns<StandardField> = [
    { title: "中文名称", dataIndex: "cnName", width: 180 },
    { title: "英文编码", dataIndex: "enCode", width: 180 },
    { title: "数据类型", dataIndex: "dataType", width: 120 },
    { title: "长度/精度", key: "precision", width: 120,
      render: (_, row) => row.lengthPrecision ?? "—" },
    { title: "安全等级", key: "security", width: 100,
      render: (_, row) => SECURITY_LABEL[row.securityLevel] },
    { title: "负责人", key: "owner", width: 140,
      render: (_, row) => row.ownerName ?? row.ownerId },
    { title: "更新时间", dataIndex: "updateTime", width: 180 },
  ];
  if (props.searching) columns.push({
    title: "所属分类", dataIndex: "categoryPath", width: 220, ellipsis: true,
  });
  columns.push({
    title: "操作", key: "actions", width: 120, fixed: "right", align: "center",
    render: (_, row) => (
      <div className="flex items-center justify-center gap-2">
        <Button variant="ghost" size="small" disabled={props.loading}
          className="px-0.5 font-normal text-[var(--yak-color-primary)]"
          onClick={() => props.onEdit(row)}>编辑</Button>
        <Button variant="ghost" size="small" disabled={props.loading}
          className="px-0.5 font-normal text-[#667085] hover:text-[#d92d20]"
          onClick={() => props.onDelete(row)}>删除</Button>
      </div>
    ),
  });
  return <Table<StandardField>
    rowKey="id" columns={columns} dataSource={props.page?.bizData ?? []}
    loading={props.loading} size="small" bordered={false} scroll={{ x: 1160 }}
    pagination={{ current: props.pageNo, pageSize: props.pageSize,
      total: props.page?.pagination.total ?? 0, disabled: props.loading,
      showSizeChanger: true, pageSizeOptions: [20, 50, 100],
      onChange: props.onPageChange }}
  />;
}
```

表头、字号、主题色和间距使用 Yak UI 当前 Token；表头浅灰、无垂直分隔线。不在页面复制 Table 内部固定列和分页算法，不为本功能改变共享组件的全局默认样式。卡片按现有设计系统使用轻量层次，避免叠加自定义大阴影。

分类私有树复用嵌套 ul/li 和 Button 的实现模式：独立的折叠按钮设置 aria-expanded，名称按钮设置 aria-current；选中样式用现有主题色和浅蓝背景 Token。节点操作在 hover、focus-within 显示，触屏保持可见。新增同级使用节点的 parentId，新增子级使用节点 id，不先改变当前选择；重命名/删除使用各自显式目标 ID。不得嵌套 button 或声明未实现完整键盘模型的 ARIA tree。

### Form core and asynchronous checks

新建默认输入可由纯函数生成，避免把当前用户固定在模块加载时的闭包内：

```ts
export const newStandardInput = (userId: string): StandardInput => ({
  cnName: "", enCode: "", dataType: "STRING", lengthPrecision: null,
  isNotNull: false, securityLevel: "INTERNAL", ownerId: userId,
  businessCaliber: null,
});

export function validateStandard(input: StandardInput, owners: readonly OwnerOption[]) {
  const errors: Partial<Record<keyof StandardInput, string>> = {};
  if (!input.cnName.trim() || input.cnName.trim().length > 128)
    errors.cnName = "请输入1至128个字符的中文名称";
  if (!/^[a-z][a-z0-9_]{0,63}$/.test(input.enCode))
    errors.enCode = "须以小写字母开头，仅含小写字母、数字或下划线，最多64位";
  if (!(["STRING", "BIGINT", "DECIMAL", "DATE", "TIMESTAMP"] as const)
      .some((value) => value === input.dataType)) errors.dataType = "请选择数据类型";
  if (!(["PUBLIC", "INTERNAL", "SENSITIVE", "CONFIDENTIAL"] as const)
      .some((value) => value === input.securityLevel)) errors.securityLevel = "请选择安全等级";
  if (typeof input.isNotNull !== "boolean") errors.isNotNull = "请选择是否非空";
  const value = input.lengthPrecision;
  if (value !== null && value !== "") {
    if (input.dataType === "STRING") {
      if (!/^[1-9][0-9]{0,9}$/.test(value) || Number(value) > 2147483647)
        errors.lengthPrecision = "请输入有效正整数长度";
    } else if (input.dataType === "DECIMAL") {
      const match = /^([1-9][0-9]?),([0-9]{1,2})$/.exec(value);
      if (!match || Number(match[1]) > 38 || Number(match[2]) > Number(match[1]))
        errors.lengthPrecision = "请输入合法精度，例如18,2";
    } else errors.lengthPrecision = "当前类型不支持长度或精度";
  }
  if (!owners.some((owner) => owner.userId === input.ownerId))
    errors.ownerId = "请选择当前工作空间有效成员";
  if ((input.businessCaliber?.length ?? 0) > 2000)
    errors.businessCaliber = "业务口径不能超过2000个字符";
  return errors;
}
```

StandardDialog 使用现有 DialogContent、DialogTitle、Form、Field、FieldLabel、FieldError 和控件，采用 104px Label 列、small + outlined、自适应 Control 列。必填通过 FieldLabel 的 required 星号与控件 required/aria-required 表达，错误与控件关联，不另绘制提示体系。

| 顺序 | 控件与绑定 |
| --- | --- |
| 所属分类 | disabled Select 或只读 Input，显示分类路径；值来自 draft，而非当前左树快照中的另一个选中项 |
| 中文名称 / 英文编码 | Input，分别绑定 cnName / enCode，FieldError 与 aria-invalid 关联 |
| 数据类型 | Select，五种固定枚举 |
| 长度/精度 | Input，仅 STRING/DECIMAL 可编辑；类型变化清空旧值 |
| 是否非空 | 页面私有原生 radio 是/否，fieldset/legend 与独立 name 关联，显式绑定 boolean；Yak UI 尚未公开 RadioGroup，不伪造组件导出或引入 UI 库 |
| 安全等级 | Select，四种固定枚举 |
| 负责人 | 可搜索 Select，当前有效成员；候选加载/失败/重试为真实状态 |
| 业务口径 | Textarea，绑定 businessCaliber |

编码正则合法后，在失焦时调用 checkCodeUnique；每次值变化取消旧校验并使旧结果失效，不对每次键入发请求。编辑携带本行 excludeId；提交仍处理数据库冲突。所有必填和长度/精度规则也在后端执行，不能只依赖 TypeScript。

提交期间使用 ref 防重入和 loading 状态；关闭、Workspace 切换或卸载后不更新旧弹窗。更新携带打开弹窗时的 revision，不在后台悄悄替换为新 revision。删除先显示确认，引用可用时可展示阻断状态，最终仍以 DELETE 结果为准。

### Persistence and initialization

业务表保留需求中的 standard_category / standard_field 概念，使用现有 `yak_ops_` 前缀。另增一张真实承担初始化与锁所有权的 catalog 元数据表，避免用户删掉默认目录后每次读取又重新生成。

| 表 | 核心字段及约束 |
| --- | --- |
| `yak_ops_standard_catalog` | workspace_id 主键、initialized_at 可空、create_time/create_by；Workspace 的一次初始化状态及稳定写锁 |
| `yak_ops_standard_category` | id 主键、workspace_id、parent_id 可空、name、revision 及 BaseEntity 审计字段 |
| `yak_ops_standard_field` | id 主键、workspace_id、category_id、cn_name、en_code、data_type、length_precision 可空、is_not_null、security_level、owner_id、business_caliber 可空、revision 及 BaseEntity 审计字段 |

- catalog 的 workspace_id 以 RESTRICT 外键指向 `yak_ops_workspace.id`；category 的 workspace_id 指向该目录册，保证初始化状态和分类拥有相同的 Workspace 生命周期。
- category 建唯一键 `(workspace_id,id)`；自引用复合外键 `(workspace_id,parent_id)` 以 RESTRICT 指向同表，NULL 仅表示根目录。
- field 的 `(workspace_id,category_id)` 复合外键 RESTRICT 指向 category。字段不得跨 Workspace 挂载分类。
- en_code 使用 ASCII 字符集、ascii_bin 排序规则和单列 UNIQUE，保证跨 Workspace 的标准唯一性；SQL CHECK 覆盖合法格式。编码长度上限 64，revision > 0，枚举与布尔值具有约束。
- 分类 `(workspace_id,parent_id)`、字段 `(workspace_id,category_id,update_time,id)` 建索引。任意位置包含搜索不承诺通过普通 B-tree 消除扫描；本次不引入全文引擎或第二份搜索数据。
- 使用应用生成 ID 和受信 actor 审计，不从请求接受 create_by/update_by。Entity 字段、Repository 方法、业务 Interface 和 SQL 表/列均按仓库规则编写英文 JavaDoc/COMMENT，不生成包含业务口径等业务载荷的 toString。

`POST /catalog/initialize` 在事务内创建或锁定当前 Workspace 的 catalog 行。初始化标记为空才创建两个默认根目录并设置 initialized_at；事务失败全部回滚。并发初始化通过唯一主键和行锁串行，重复调用返回当前树；删除或重命名默认目录不清空初始化标记。GET 不隐式执行初始化。其他写接口在内部确保目录册初始化，外部调用者不必依赖先调用某个 GET 的隐藏顺序。

### Transactions and deletion safety

本次是管理类元数据写入。为简化多级目录、递归删除检查和字段归属的并发契约，**所有同 Workspace 的标准写操作先锁 catalog 行，再锁目标目录/字段**；不同 Workspace 不共用该锁。它是业务事务中的短时数据库锁，不是前端锁或进程内锁。若后续吞吐需要更细锁定，必须保留同样的可观察安全语义。

- 新建目录验证父级、深度及 Workspace；不提供改 parentId 的接口，因此支持的操作不能制造目录环。
- 删除目录在锁内读取后代及其挂载字段；任何后代分类或字段存在均返回 409，不执行级联删除。复合外键提供最终兜底，SQL 使用参数绑定。
- 标准创建/修改/删除在同一锁顺序下完成，categoryId 固定。删除目录与新建后代/字段不能同时成功而留下孤儿。
- 编码跨 Workspace 的竞争由 en_code UNIQUE 裁决；映射 DuplicateKeyException，不解析数据库错误文本。失败写入和审计更新在同一事务回滚。
- 更新和删除检查当前 revision；成功更新 revision + 1。没有字段变化的保存也具有明确版本推进，不依赖驱动的 matched/affected rows 偶然行为。
- 真实逻辑模型引用接入后，必须遵守 catalog/标准写锁和外键约束；读取引用数量与 DELETE 之间的竞争不能绕过限制。
- 锁查询要通过实际 Boot MyBatis 拦截链验证合法 MySQL 子句顺序，保留 Security 用户查询的 app_name 隔离；不全局禁用拦截器。

## Compatibility, Deprecation, and Migration Plan

1. 本提案接受前不增加菜单、REST、业务类或 SQL。提案本身只新增文档，没有运行行为或数据库变化。
2. 后续实现基于已合入的数仓规划代码，在数据建模菜单下新增独立入口；不修改数仓规划编码、数据和接口，不改变已有 Task/Runtime 契约。
3. 提议以 1.5.0 承载数据标准，使用新 `V3__v1_5_0.sql`，要求 V2 已按 1.4.0 的发布顺序存在。保留 V1 和已被执行的 V2；不因同一天开发多个功能而改写已验证或已应用迁移的 checksum。
4. 使用已有版本准备工具统一 Maven、前端、Compose 和 release.env；迁移版本检查与元数据检查保持启用。本提案不执行版本变更、发布、部署或打 tag。
5. 默认分类通过显式初始化事务产生，不在 migration 中重写 Workspace、用户或业务行。旧版数据库仍遵循既有 v1.3.0 基线兼容约定，本模块不提供 v1.0–v1.2 数据库自动升级路径。
6. 若实施前发布序列有其他迁移插入，按当时最新版本选择新的顺序号和产品版本后记录到实现 PR；不得复用或改写已执行迁移。
7. 字段属性自动继承及标准变更对模型的传播另行设计。当前编辑标准不会声称已同步任何逻辑模型。

## Test Plan

以下是实现阶段的拟议验证范围；不是本文档已经运行或通过的测试报告。本提案不增加或执行测试。

- 业务规则：名称/编码/枚举/非空/精度和长度、默认值、负责人失效、revision 冲突、标准编码与规划编码独立。
- 真 MySQL：跨 Workspace 唯一性竞争、同值编码更新、失败事务回滚、复合外键隔离、多级目录删除、创建子目录/字段与删除父级竞争、并发初始化只产生一组默认根、默认目录删除后不复活。
- Boot 真实 HTTP：完整分类与字段 CRUD、所有字段回读、跨 Workspace ID 404、全局搜索/转义/分页快照、错误状态码及中文业务提示、NOT_ENABLED 引用语义；必须包含生产 MyBatis 插件链。
- 浏览器：多级展开和同级/子级创建、左树右表联动、全局搜索及真实所属分类、分页与删除回退、逐行编辑/删除、只读分类、负责人选择、编码旧请求竞争、弹窗失败保留输入、Workspace 切换、键盘、窄屏和减少动画。
- 使用既有 JUnit 根资源和套件选择；具体 *IT 注册到 manifest，HTTP 场景归 yak-ops-e2e；浏览器扩展稳定建模业务场景，不创建临时 per-PR runner。
- 实现 PR 按实际执行范围报告 Oxfmt、Oxlint、TypeScript、架构、构建、Spotless、Checkstyle、数据库/HTTP/浏览器结果。未运行或跳过的测试不得写成通过；CI 与 Release Gate 门禁不削弱。

## Rejected Alternatives

- **直接开始产品实现**：新增 REST 和持久化子系统需要按 AGENTS.md 与 CONTRIBUTING.md 先讨论和接受 YOIP。
- **把标准塞进数仓规划的三级节点表**：标准目录支持多级且没有规划节点类型；字段还有类型、精度和分页生命周期，应有独立所有权。
- **每次 GET 发现目录为空就插入默认根**：GET 带写入副作用，并会让用户删除的默认目录复活。选择一次初始化标记和显式写接口。
- **用系统用户列表选负责人**：跨越 Workspace 候选边界并返回多余账号信息；采用授权成员最小投影。
- **只由前端预检查编码或删除引用**：存在请求竞争和非 UI 调用者；采用数据库唯一/外键、业务事务和最终 DELETE 校验。
- **返回 referenced=false 的占位实现**：混淆未启用和已验证无引用；采用明确 NOT_ENABLED 状态，不为未来模块建立伪造引用记录。
- **在当前 V2 中追加表或关闭迁移版本门禁**：V2 已作为数仓规划测试对象且可能被应用；选择后续顺序迁移和一致的目标版本。
- **通用树/表单配置引擎或新 UI 库**：当前没有实际共享需求，增加调用约束和视觉偏差；沿用私有目录与 Yak UI 公共组件。
