# Toast 契约

Status: Active

实现与公开参数：[Toast.tsx](../src/toast/Toast.tsx)。常驻风险提示与字段错误的选用见 [Alert](alert.md#when-to-use)。

## Ownership

Toast 提供瞬时结果反馈，替代页面手写的 message / notification。Provider 负责共享通知宿主，调用方使用 toast.success / error / warning / info，业务文案与 action 回调不进入组件内部。

## Presentation

- 右上角、中性玻璃表面；状态图标和淡色光晕表达 tone，不把整张卡片染成状态色。
- title / description / meta 保持层次，可选 action 由业务组合，关闭按钮保留可访问名称；复制错误、诊断与重试逻辑属于产品。
- 默认最多可见三条，前卡可读、后卡轻量叠放；hover 或 focus 展开，不增长成无界列表。
- 入场、离场及滑动关闭使用柔和位移/透明度；叠放展开使用独立的高度布局过渡。
- 减少动态效果时关闭过渡。宽度、间距、图标尺寸、默认 timeout 和具体时序直接读取源码及 Token，不在通用规则重复定义。

## 人工验收

连续触发不同 tone，检查三条可见上限、堆叠与 hover/focus 展开；长标题、description 和 meta 不溢出；action 与关闭可用；滑动退出和 reduced-motion 不留下透明可点击区域。瞬时 Toast 不能替代必须持续可见的风险提示。
