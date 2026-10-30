import { Button, Form, Input } from "@yak-ops/yak-ui";
import { useEffect, useRef, type ReactNode } from "react";

interface Props {
  disabled: boolean;
  searching: boolean;
  searchText: string;
  onSearchTextChange: (value: string) => void;
  onSearch: () => void;
  onClear: () => void;
  createLabel: string;
  onCreate: () => void;
  hint?: ReactNode;
}

/** Search and create controls shared by the five metric kinds. */
export default function MetricToolbar(props: Props) {
  // Same search rhythm as the standard toolbars: typing searches after a pause, the button is the
  // immediate path, and both use the latest closure so a pending timer cannot submit stale text.
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const latestSearch = useRef(props.onSearch);
  latestSearch.current = props.onSearch;
  const stopTimer = () => {
    if (timer.current !== undefined) clearTimeout(timer.current);
    timer.current = undefined;
  };
  useEffect(
    () => () => {
      if (timer.current !== undefined) clearTimeout(timer.current);
    },
    [],
  );
  const searchNow = () => {
    stopTimer();
    props.onSearch();
  };
  const clear = () => {
    stopTimer();
    props.onClear();
  };
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--yak-components-control-border)] px-4 py-3">
      <Form
        className="flex max-w-full items-center gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          searchNow();
        }}
      >
        <Input
          size="small"
          variant="outlined"
          value={props.searchText}
          maxLength={128}
          disabled={props.disabled}
          aria-label="搜索当前指标种类"
          placeholder="中文名称或英文编码"
          onChange={(event) => {
            props.onSearchTextChange(event.target.value);
            stopTimer();
            timer.current = setTimeout(() => latestSearch.current(), 300);
          }}
        />
        <Button size="small" type="submit" disabled={props.disabled}>
          搜索
        </Button>
        {(props.searchText || props.searching) && (
          <Button size="small" disabled={props.disabled} onClick={clear}>
            清空
          </Button>
        )}
      </Form>
      <div className="flex items-center gap-3">
        {props.hint}
        <Button size="small" variant="primary" disabled={props.disabled} onClick={props.onCreate}>
          {props.createLabel}
        </Button>
      </div>
    </div>
  );
}
