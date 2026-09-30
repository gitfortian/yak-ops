# Table 契约

Status: Active

公开类型见 [interface.ts](../src/table/interface.ts)，渲染与加载见 [InternalTable](../src/table/InternalTable.tsx)，状态逻辑见 [hooks](../src/table/hooks/)。

## Data / State

columns / dataSource / rowKey 定义表格，列 render 只负责内容组合。业务持有请求、筛选表单、CRUD 和后端分页适配，Table 不实现第二套数据请求层。

- 不提供 pagination.total 时可以对传入数据本地分页；提供 total 时把 dataSource 视为服务器当前页，不再二次切片。
- 本地过滤、排序先于分页。比较函数启用本地排序，sorter: true 仅上报服务端排序意图；当前是单列排序。
- onFilter 提供本地过滤；没有 onFilter 的 filters 由调用方处理。支持现有受控 sortOrder / filteredValue 及对应默认值。
- onChange(pagination, filters, sorter, extra) 是分页/过滤/排序的统一通知边界。
- rowSelection 支持受控/非受控，选择列由通用逻辑注入；getCheckboxProps 决定禁用。全选只作用当前页可选记录，跨页已选 key 保留，不加入业务选择规则。
- 分页复用共享 Pagination；footer 是其左侧通用插槽，不拥有批量操作生命周期。pageSizeLabel 由调用方提供文案。

## 视觉与布局约定

表头使用共享灰色 Token，不画外框或列间竖线。bordered 默认为 false，只保留数据行底线；true 时只由数据单元格组成完整网格，首末列与首末行边界闭合、相邻边线不叠加。

网格随实际数据结束，外层滚动容器不画框也不填充剩余高度。页面可保留满高白底，min-h-full 只能作用外壳，不能把数据区撑高或将分页推到页面底部。

scroll.y 是最大滚动高度而不是固定高度，少量数据不补空白。表头和表体仍由同一个原生 table 对齐，保留横向滚动与 sticky。

固定列使用 Column 的 `fixed: "left" | "right"`。固定列必须提供明确 `width`，Table 根据同侧相邻固定列宽度自动累计 offset；表头与表体共用同一 offset，边界列提供轻量阴影。业务页面不得重复手写 sticky/right/left 固定列逻辑。

footer / Pagination 在数据视口后留 12px 间距，位于网格及滚动范围外，不画额外顶线或用 mt-auto 底部定位。分页保持单行紧凑，激活状态和选中 Checkbox 使用共享主色。

## 加载与空态

首次加载保留稳定最小表体占位，不同时显示空态文字；空数据使用相同边框边界。刷新时原数据不卸载，分页可见但禁用。

遮罩只覆盖可见表体，不进入表头、滚动条、分页或剩余白底。起点测量实际表头单元格，不猜尺寸；sticky 时不能用滚出视口的 thead 代替 th。跟随视口/表头尺寸和滚动，结束后清理监听与观察器。

## 当前边界

支持当前行 hover、选中、ellipsis、尺寸、分页、单列排序、过滤、滚动及左右固定列；没有承诺展开行、虚拟滚动、多列排序或完整 AntD 兼容层。

## 验收步骤

- 单行、改变窗口高度：网格紧随这一行结束，表头无外框，分页跟随数据
- 多行、多行单元格：网格四边闭合，相邻边线不双线
- bordered=false：只有行底线，不出现左右外框及竖线
- 空数据、首次加载：占位稳定，空态/加载不同时出现，表头不遮挡
- 刷新已有数据：原行保留，遮罩只覆盖可见表体，分页禁用
- 横向滚动到最右侧：表头和列对齐，末列有边，遮罩不随内容横移
- 左/右固定列：横向滚动时保持位置；多个同侧固定列 offset 正确；hover、选中、bordered、sticky header 与 loading 遮罩不破坏固定列背景和层级
- scroll.y + sticky 开关：少量数据不撑高；吸顶表头不遮挡，非吸顶离开后起点正确
- 切换尺寸、换行表头：遮罩随真实尺寸更新，不残留旧高度
- 禁用行、跨页选择及筛选：当前页全选不含禁用行，跨页 key 保留，业务清理策略仍由调用方负责
- 本地/服务端分页、排序、过滤：无二次分页，事件与数据处理责任符合 Data / State

验收方法不是执行结果，具体记录见 [Tooling](../../../docs/tooling.md#verification-record)。
