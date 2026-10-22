import {
  Field,
  FieldError,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
export const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";
interface InputProps {
  id: string;
  label: string;
  value: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
  error?: string;
  required?: boolean;
  disabled?: boolean;
  maxLength?: number;
  readOnly?: boolean;
}
export function InputField(props: InputProps) {
  return (
    <Field className={FIELD_CLASS} invalid={Boolean(props.error)}>
      <FieldLabel htmlFor={props.id} required={props.required} className="pt-1">
        {props.label}
      </FieldLabel>
      <div>
        <Input
          id={props.id}
          size="small"
          variant="outlined"
          value={props.value}
          disabled={props.disabled}
          required={props.required}
          maxLength={props.maxLength}
          readOnly={props.readOnly}
          aria-invalid={Boolean(props.error) || undefined}
          onChange={(event) => props.onChange?.(event.target.value)}
          onBlur={props.onBlur}
        />
        <FieldError match={Boolean(props.error)}>{props.error}</FieldError>
      </div>
    </Field>
  );
}
export function SelectField(props: {
  id: string;
  label: string;
  value: string;
  items: { value: string; label: string; disabled?: boolean }[];
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  required?: boolean;
}) {
  const required = props.required ?? true;
  return (
    <Field className={FIELD_CLASS} invalid={Boolean(props.error)}>
      <FieldLabel htmlFor={props.id} required={required} className="pt-1">
        {props.label}
      </FieldLabel>
      <div>
        <Select
          value={props.value || null}
          size="small"
          items={props.items}
          disabled={props.disabled}
          onValueChange={(value) => props.onChange(value ?? "")}
        >
          <SelectTrigger
            id={props.id}
            variant="outlined"
            aria-required={required}
            aria-invalid={Boolean(props.error) || undefined}
          >
            <SelectValue placeholder="请选择" />
          </SelectTrigger>
          <SelectContent>
            {props.items.map((item) => (
              <SelectItem key={item.value} value={item.value} disabled={item.disabled}>
                <SelectItemText>{item.label}</SelectItemText>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <FieldError match={Boolean(props.error)}>{props.error}</FieldError>
      </div>
    </Field>
  );
}
