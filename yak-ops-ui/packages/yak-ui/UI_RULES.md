# Yak UI Rules

Scope:

- `yak-ops-ui/packages/yak-ui/**`

Depends On:

- `../../FRONTEND_RULES.md`
- `../../ARCHITECTURE.md`

Owns:

- Yak Ops business-agnostic UI Primitive
- Primitive public Props Contract
- keyboard / focus / disabled / accessibility behavior
- Yak UI Design Token and visual state

Public Import:

- `@yak-ops/yak-ui`

## Dependency Direction

```text
apps / product packages
        ↓
@yak-ops/yak-ui
        ↓
@base-ui/react
        ↓
DOM
```

Base UI is an implementation dependency, not a product-facing API.

## Must

- Primitive must not contain Login / Datasource or other product semantics.
- App and product packages use public Yak UI exports instead of importing Base UI directly.
- Headless interaction / accessibility should come from Base UI when it already owns the behavior.
- Yak UI owns stable Props, composition API, Design Token and visual states.
- Tailwind + Yak UI tokens are the styling foundation.
- `--yak-color-primary` is the shared primary accent token; current baseline is `#0033FF`. Component active / focus colors should reference it instead of duplicating blue literals.
- Button / Input / Textarea / SelectTrigger share control-radius tokens instead of hard-coded radius values: small `6px`, medium `8px`, large `10px`. Product code should not redefine these control radii through `className`.
- Variant contracts use `class-variance-authority` when variants are real product-wide concepts.
- Button defaults to `type="button"`.
- Button `primary` uses `--yak-color-primary` as its source color; hover / active are derived from that token rather than maintaining a separate dark primary palette.
- Button `loading` replaces the visible action content with one centered indicator while preserving the original content width, accessible name, disabled behavior and `aria-busy` state. The indicator uses a static outer track plus a smaller rotating inner arc with `currentColor`; reduced-motion disables the rotation. Product code must not swap loading copy or hand-build a second spinner beside the label.
- Input / Textarea / NumberField use one shared input visual language.
- Input / Textarea / SelectTrigger / ComboboxInput expose `filled` as the default surface and `outlined` for explicit white/surface controls with a visible shared border token; product code must use the variant instead of fighting `border-transparent` through `className`.
- Input / Textarea / ComboboxInput focus and SelectTrigger focus/open use the primary border only; they do not add a focus box-shadow / ring. Textarea keeps only its multiline-specific height, vertical padding and resize behavior; radius, typography, surface and interaction states follow Input.
- Combobox is the searchable selection primitive for larger option sets. It shares Input size/radius/variant styling and Select popup/item tokens, supports Base UI single/multiple selection, and exposes a selected-item indicator without product-specific semantics.
- Combobox empty-state presentation must preserve Base UI's mounted live region for accessibility. When the list is not empty, the mounted Empty node must collapse to zero layout space; do not hide or unmount it just to remove visual spacing.
- Select / Menu / Tooltip / Popover / Dialog / Drawer / Tabs remain compositional instead of becoming giant convenience-prop components.
- Select separates domain values from user-visible labels: when `value` and `label` differ, product code must pass `items` to `Select` so `SelectValue` renders the matching label. Controlled state and `onValueChange` continue to use the domain `value`; product code must not duplicate value-to-label lookup logic inside the trigger. Resource IDs and internal composite keys are domain values and must never become the closed-trigger display text when a human-readable label exists.
- Select popup composition stays generic: `SelectContent.header` may host `SelectSearch`, `SelectSearch.extra` may host refresh or other actions, `SelectContent.emptyContent` owns generic empty presentation, and `SelectContent.footer` may host `SelectFooter` with arbitrary product-owned actions. Yak UI owns popup layout and keyboard isolation only; filtering state, remote search, refresh requests, CRUD and footer business behavior remain in the app layer.
- Select popup motion uses Base UI starting / ending states with `clip-path + opacity`, revealing from the trigger-facing edge according to the actual Popup `data-side` after collision handling. Keep Portal / Positioner and the full popup layout geometry unchanged; never scale text, search controls or footer content, and never animate popup `height`, `max-height` or `grid-template-rows`.
- Select reveal opens in 360ms with `cubic-bezier(0.16,1,0.3,1)` while opacity enters in 160ms; close uses 220ms reveal and 180ms opacity with `cubic-bezier(0.2,0,0,1)`. The chevron transitions its CSS `rotate` property in 360ms / 220ms, and option background feedback uses 150ms. All three honor reduced-motion preferences by disabling transitions.
- Select reveal preserves the existing shadow with a 64px negative clip inset at rest; this is a visual gutter, not layout padding. Starting / ending clips collapse at the trigger-facing edge, and the ending Popup does not accept pointer events. Keep CSS transitions on Popup so Base UI owns completion and interrupted open / close; do not add animation timers, a second open state, bounce / spring motion, or product-level motion overrides through `className`. See `docs/select-motion.md` for the contract and manual acceptance.
- Modal is the shared product-facing dialog shell: it owns title, close affordance, scrollable body, fixed footer, width and placement; product code owns business content, step state and submit lifecycle.
- Modal defaults to the existing top-offset placement. `centered` is an explicit opt-in for short, stable-height content that comfortably fits the viewport. Long forms, Wizards, Table/search-result surfaces and content likely to scroll keep the default top-offset placement. Product code must not emulate centered placement through `className`.
- Modal outside-press dismissal is opt-in. `maskClosable` defaults to `false`; only an explicit `maskClosable={true}` allows backdrop / outside presses to close it. The close button and Escape key remain available.
- Modal placement and motion are separate concerns: the outer positioning layer owns top-offset vs centered placement, while Popup owns the fade + subtle vertical motion. Popup open starts about 12px below and settles in about 140ms with a decelerating curve; close moves only about 4px upward and fades out in about 180ms with a softer decelerating curve to reduce subpixel shimmer on text and borders. Backdrop fades in about 180ms and fades out about 200ms so the overlay finishes slightly after the surface instead of snapping away at the same instant. Modal never uses scale, bounce or spring motion, and product code must not override Modal motion through `className`.
- Drawer motion is enabled by default; product flows that intentionally need immediate open / close use the explicit `animated={false}` opt-out instead of overriding transition classes through `className`.
- Dialog / Modal / Drawer / Popover / Menu popup interaction, focus restore, Escape and outside press behavior stay in Base UI.
- Toast is the common replacement for message / notification feedback.
- Toast Visual Contract V2 uses a top-right 360px glass-panel card with a subtle tone halo, a semantic 20px status icon, clear title / description / meta hierarchy and a 28px icon-button close affordance. Product code must not recreate Toast visuals.
- Toast notifications stack by default instead of growing as an unbounded vertical list. The front card remains fully readable, cards behind it use a small peek / scale treatment, hover or focus expands the stack, and the default visible limit is three.
- Toast enter / exit motion is transform + opacity only: cards enter from above, leave upward or in the swipe direction, and use a smooth decelerating curve without bounce or spring motion.
- Toast tone is semantic only: success / error / warning / info own icon and halo color, while the main card remains a neutral panel. Do not tint the whole Toast surface with status colors.
- Toast keeps generic action composition only. Error-copy, retry, request diagnostics and other product-specific behavior stay in the app layer.
- Alert is the shared inline warning primitive for persistent page-level cautions such as operation prerequisites, compatibility limits, risky side effects and explicit “avoid pitfalls” guidance.
- Alert V1 is warning-only and content-only: product code passes the warning copy through `children`; Yak UI owns the icon, surface, spacing, radius, color tokens and accessibility role. Do not add business actions, dismiss lifecycle or product-specific props to Alert.
- Persistent warnings that the user should see before acting use Alert instead of Toast. Transient save / request / execution feedback continues to use Toast.
- Product code must not hand-build yellow warning boxes when Alert fits the case.
- Badge is the common lightweight status-label primitive; product-specific status semantics stay outside Yak UI.
- Collapsible remains the low-level disclosure primitive. CollapseSection is the shared configuration-section shell for a titled, full-row trigger plus animated content: the 32px header owns border, neutral surface, hover, pointer cursor, focus border and chevron rotation; the panel owns the 12px header-to-content gap. Product code owns the body layout and business state. `extra` is presentation-only and must not contain nested interactive controls inside the trigger.
- Table owns generic tabular rendering, loading / empty presentation, scroll / sticky header and pagination placement; product code owns fetching, filters, mutations and business cell content.
- PageHeader owns generic page title, description, right-side composition and optional divider; product code owns page actions and business behavior.
- `className` is a layout / positioning / necessary escape hatch, not a second visual contract.

## Select Popup Composition

Select keeps the existing simple option-list API and adds optional popup regions without forcing product code into a second component.

```tsx
<SelectContent
  header={
    <SelectSearch
      value={keyword}
      onChange={(event) => setKeyword(event.target.value)}
      extra={<Button onClick={refresh}>...</Button>}
    />
  }
  emptyContent={filteredItems.length === 0 ? emptyText : undefined}
  footer={
    <SelectFooter>
      <Button onClick={createItem}>...</Button>
    </SelectFooter>
  }
>
  {filteredItems.map((item) => (
    <SelectItem key={item.value} value={item.value}>
      <SelectItemText>{item.label}</SelectItemText>
    </SelectItem>
  ))}
</SelectContent>
```

Contract:

- Existing `<SelectContent><SelectItem ... /></SelectContent>` remains valid.
- `SelectSearch` is popup-local input presentation. It does not own local filtering or remote querying.
- `SelectSearch.extra` is a generic React composition slot; refresh/loading/create semantics belong to product code.
- Search input typing and cursor keys stay inside the input instead of leaking into Select typeahead/list navigation; Escape remains available to close the popup and Tab keeps normal focus behavior.
- `emptyContent` replaces the option list body for the current render while keeping the Select popup structure mounted.
- `SelectFooter` owns only border, spacing and layout. Its children and click handlers are external product content.
- Do not add Datasource-specific create/refresh props to Select.

## Form Boundary

Yak UI Form / Field only own:

- semantic form container
- label / description / error presentation
- required-field marker presentation through `FieldLabel required` / `FieldRequiredMark`
- accessibility relationship
- control visual state

Yak UI does not own:

- Datasource form schema
- field dependency rules
- dynamic visibility
- cross-field business validation
- form list business data
- API payload assembly
- submit lifecycle

Product form state belongs to the owning product package. Product validation messages stay in the product i18n layer and are passed to `FieldError`; Yak UI must not own Datasource-specific required copy or validation rules.

Do not rebuild an AntD-style mega Form API inside Yak UI.

## Table Boundary

Yak UI Table uses an AntD-familiar core contract without becoming an AntD compatibility layer:

- `columns / dataSource / rowKey` define generic tabular data.
- Column `render` owns presentation composition but not product data fetching.
- `pagination` reuses Yak UI Pagination; Table does not implement a second pagination control.
- When `pagination.total` is omitted, Table may paginate the supplied in-memory `dataSource`.
- When `pagination.total` is provided, Table treats `dataSource` as the already-paged server result.
- `loading` keeps the current table structure mounted instead of replacing it. The loading mask starts below the header and covers only the Table Body, so the header surface remains visually stable; first-load empty data reserves a stable body height without rendering Empty under the mask, and Pagination stays visible but disabled.
- `rowSelection` is a generic controlled / uncontrolled selection contract implemented by injecting a selection column; Table Body does not hard-code Checkbox behavior.
- `footer` is a generic left-side composition slot sharing the bottom row with Pagination; batch / CRUD semantics remain product-owned.
- Select-all only affects selectable rows on the currently rendered page and preserves selected keys from other pages.
- Disabled row selection comes from `getCheckboxProps`; selection never contains Datasource or other domain semantics.
- Sorter is a single-column AntD-familiar contract: comparator function enables local sorting; `sorter: true` exposes sort state for server-side handling without reordering local rows.
- Filter state is per-column: `onFilter` enables local filtering; columns with `filters` but no `onFilter` expose controlled filter state for server-side handling.
- Controlled `sortOrder / filteredValue` and uncontrolled `defaultSortOrder / defaultFilteredValue` are both supported.
- Table-level `onChange(pagination, filters, sorter, extra)` is the single generic notification boundary for paginate / sort / filter changes.
- Local filter and sort run before local pagination; server pagination remains owned by the product when `pagination.total` is provided.
- Current Table supports size, border, row hover, selected-row state, ellipsis, sort, filter, horizontal / vertical scroll and sticky header.
- Table header uses the shared `#F2F2F2` surface without an outer frame or internal header dividers. `bordered` applies only to the complete body grid, including its first/last column edges and first/last row edges; scroll containers and the footer never draw the grid frame.
- Table data height follows content. Internal scroll/layout wrappers must not grow to fill spare page height. Existing product `min-h-full` may size the outer shell only; it must not stretch the data grid or push Pagination to the page bottom. `scroll.y` remains an opt-in maximum viewport height, not a fixed body height.
- Body cells own the shared border token and collapsed grid lines. Unbordered tables retain row bottom separators; empty/initial-loading cells preserve their stable minimum body height and follow the same border contract.
- Footer/Pagination follow the data viewport with a 12px gap, outside its scroll and border region, without an extra top divider or `mt-auto` bottom pinning.
- Loading uses a viewport-local sibling overlay, measured from the actual header cell rather than a size-based top offset. It follows resize/scroll changes, preserves sticky headers, excludes scrollbars/footer/spare page space, and cleans up observers/listeners after loading.
- See `docs/table.md` for the body-only border contract and manual acceptance scenarios.
- Pagination is a single-line compact control; the active page uses `--yak-color-primary` instead of Button primary styling, and page-size wording is supplied through the generic `pageSizeLabel` composition point.
- Checked Table selection controls use the shared primary token; product code must not invent a separate selection blue.
- Expandable rows, fixed columns, virtualization, multi-column sort and component overrides remain deferred.

Table must not own:

- request / API lifecycle
- search form or filter schema
- CRUD actions
- Datasource-specific columns
- backend pagination contract adaptation

## Upload Boundary

Yak UI does not provide an Upload product component.

File selection uses native browser file input. Upload request, file type / size policy, progress, retry and backend contract belong to the owning product package.

## Legacy Replacement Reference

```text
AntD Alert           → Alert
AntD Button          → Button
AntD Checkbox        → Checkbox
AntD Input           → Input
AntD Input.Password  → PasswordInput
AntD Input.TextArea  → Textarea
AntD InputNumber     → NumberField
AntD Form            → Form + Field presentation; product owns form state
AntD Select          → Select / Combobox when filtering is required
AntD Switch          → Switch
AntD Tooltip         → Tooltip
AntD Popover         → Popover
AntD Modal           → Modal
AntD Drawer          → Drawer
AntD Tabs            → Tabs
AntD Dropdown        → DropdownMenu
AntD Pagination      → Pagination
AntD Table           → Table
AntD Spin            → Spinner
AntD Empty           → Empty
AntD Collapse        → Collapsible
AntD Tag             → Badge
AntD message         → Toast
AntD notification    → Toast
AntD Upload          → native file input + product upload logic
AntD Space           → normal flex / grid layout
```

## Must Not

- App / product packages import `@base-ui/react` directly.
- Yak UI requests APIs or reads product services.
- Primitive names or Props expose Datasource-specific concepts.
- Yak UI re-exports raw Base UI components as its public contract without an intentional Yak UI boundary.
- Add Ant Design or a second UI framework such as MUI / Chakra inside Yak UI.
- Recreate AntD-compatible APIs just to make migration search-and-replace easier.
- Put request, search-form, CRUD or domain-specific behavior inside Table.
- Add product-specific filter controls or backend query assembly to Table sort / filter hooks.
- Add future primitives that have no real current migration or product need.

## Current Set

```text
Yak UI
├── Alert
├── Badge
├── Button
├── Checkbox
├── CollapseSection
├── Collapsible
├── Combobox
├── Dialog
├── Drawer
├── DropdownMenu
├── Empty
├── Field
├── Form
├── Input
├── Modal
├── PasswordInput
├── NumberField
├── PageHeader
├── Pagination
├── Popover
├── Select
├── Spinner
├── Switch
├── Table
├── Tabs
├── Textarea
├── Toast
└── Tooltip
```

This set is the current Yak Ops UI foundation. New primitives remain problem-driven.

## Boundary

`packages/yak-ui` is the internal Yak Ops UI package.

Cross-app publication is not a current requirement. Do not introduce a separate external publishing workflow until a real consumer exists.
