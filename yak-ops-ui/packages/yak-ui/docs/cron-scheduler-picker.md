# Cron Scheduler Picker

Status: Active

Component: `CronSchedulerPicker`

## Purpose

Cron Scheduler Picker is Yak UI's reusable Quartz six-field Cron editor. It provides a compact trigger plus a large configuration popup so product pages do not have to expose a raw Cron text field as the primary interaction.

The component owns only Cron editing interaction. It does not call APIs, calculate server-side next-fire times, choose a business timezone, enable schedules, or persist schedule definitions.

## Contract

- `value` / `onValueChange` are controlled Quartz Cron strings.
- Empty `value` is allowed. Opening an empty picker starts from the visual default of daily 02:00, but nothing is committed until Confirm.
- Common mode supports minute, hour, day, week, month, and year patterns.
- The visual codec emits six fields: `second minute hour day-of-month month day-of-week`.
- Existing expressions outside the visual subset open in Advanced Cron mode and are preserved as raw text.
- Confirm commits the current draft. Cancel, Escape, or dismissing the popup leaves the caller's value unchanged.
- Clear emits an empty string when `allowClear` is enabled.
- `renderPanelExtra(draftCronExpression)` is the composition slot for product-owned preview or supporting content. It receives the current uncommitted draft and must not be used by Yak UI to perform network requests.
- The popup uses Yak UI controls and Base UI lifecycle only; it must not depend on Ant Design or product services.
- Selects rendered inside the Cron popup must raise their portaled positioner above the Cron Popover surface. Keep this override local to Cron Scheduler Picker; do not raise the global Select z-index.
- Future-fire preview belongs to the consuming product because it must use the product scheduler's real timezone and runtime semantics.

## Visual Subset

The common editor supports:

- minute interval within a start/end time range,
- hourly range or selected hours,
- daily time,
- one or more weekdays,
- monthly single date rule,
- yearly selected months plus a single date rule.

Advanced Quartz expressions that cannot be represented losslessly remain editable through Advanced Cron instead of being rewritten.

## Accessibility

The trigger is keyboard-focusable and exposes popup state. Controls keep native/Base UI keyboard behavior. Disabled state applies to the trigger and all editor actions.

## Boundary

Do not add schedule enable/disable, timezone ownership, next-run API calls, retry policy, or product validation into this component. Those belong to the caller.
