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
  Textarea,
} from "@yak-ops/yak-ui";

export const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

interface InputFieldProps {
  id: string;
  label: string;
  value: string;
  onChange?: (value: string) => void;
  onBlur?: () => void;
  error?: string;
  hint?: string;
  required?: boolean;
  disabled?: boolean;
  maxLength?: number;
  readOnly?: boolean;
  placeholder?: string;
}

export function InputField(props: InputFieldProps) {
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
          aria-required={props.required || undefined}
          maxLength={props.maxLength}
          readOnly={props.readOnly}
          placeholder={props.placeholder}
          aria-invalid={Boolean(props.error) || undefined}
          onChange={(event) => props.onChange?.(event.target.value)}
          onBlur={props.onBlur}
        />
        {/* No native `required`: Base UI's Form blocks the submit on native constraint state before
            the app validator runs, which silently retires every message that validator produces. */}
        {props.error ? <FieldError match>{props.error}</FieldError> : null}
        {props.hint && !props.error && (
          <p className="mt-1 text-xs leading-4 text-[var(--yak-color-text-secondary)]">
            {props.hint}
          </p>
        )}
      </div>
    </Field>
  );
}

interface SelectFieldProps {
  id: string;
  label: string;
  value: string;
  items: { value: string; label: string; disabled?: boolean }[];
  onChange: (value: string) => void;
  error?: string;
  hint?: string;
  disabled?: boolean;
  required?: boolean;
  placeholder?: string;
}

export function SelectField(props: SelectFieldProps) {
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
            <SelectValue placeholder={props.placeholder ?? "请选择"} />
          </SelectTrigger>
          <SelectContent>
            {props.items.map((item) => (
              <SelectItem key={item.value} value={item.value} disabled={item.disabled}>
                <SelectItemText>{item.label}</SelectItemText>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {props.error ? <FieldError match>{props.error}</FieldError> : null}
        {props.hint && !props.error && (
          <p className="mt-1 text-xs leading-4 text-[var(--yak-color-text-secondary)]">
            {props.hint}
          </p>
        )}
      </div>
    </Field>
  );
}

interface TextareaFieldProps {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  required?: boolean;
  disabled?: boolean;
  rows?: number;
  maxLength?: number;
  placeholder?: string;
}

export function TextareaField(props: TextareaFieldProps) {
  return (
    <Field className={FIELD_CLASS} invalid={Boolean(props.error)}>
      <FieldLabel htmlFor={props.id} required={props.required} className="pt-1">
        {props.label}
      </FieldLabel>
      <div>
        <Textarea
          id={props.id}
          size="small"
          variant="outlined"
          value={props.value}
          rows={props.rows ?? 3}
          maxLength={props.maxLength}
          disabled={props.disabled}
          placeholder={props.placeholder}
          onValueChange={(value) => props.onChange(String(value ?? ""))}
        />
        {props.error ? <FieldError match>{props.error}</FieldError> : null}
      </div>
    </Field>
  );
}
