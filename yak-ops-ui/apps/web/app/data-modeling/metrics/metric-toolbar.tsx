import { Button, Form, Input } from "@yak-ops/yak-ui";

interface Props {
  disabled: boolean;
  searching: boolean;
  searchText: string;
  onSearchTextChange: (value: string) => void;
  onSearch: () => void;
  onClear: () => void;
  createLabel: string;
  onCreate: () => void;
  hint?: React.ReactNode;
}

/** Search and create controls shared by the five metric kinds. */
export default function MetricToolbar(props: Props) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <Form
        className="flex max-w-full items-center gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          props.onSearch();
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
          onChange={(event) => props.onSearchTextChange(event.target.value)}
        />
        <Button size="small" type="submit" disabled={props.disabled}>
          搜索
        </Button>
        {(props.searchText || props.searching) && (
          <Button size="small" disabled={props.disabled} onClick={props.onClear}>
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
