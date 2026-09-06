import { Alert, Input, InputNumber, Select, Spin, Switch } from 'antd';
import type { ReactNode } from 'react';

import type {
  LinkUpConnectorSchema,
  OfflineConnectorRole,
} from '@/services/batch-link-up';

import {
  connectorTaskOptionFields,
  type ConnectorTaskOptionField,
} from '../form-schema/connectorTaskOptions';

interface ConnectorExtraParametersProps {
  role: OfflineConnectorRole;
  schema: LinkUpConnectorSchema | null;
  loading: boolean;
  error: string;
  config: Record<string, any>;
  onChange: (patch: Record<string, any>) => void;
}

function FieldLabel({
  label,
  required,
}: {
  label: string;
  required: boolean;
}) {
  return (
    <div className="mb-2 text-[12px] font-medium text-[#475467]">
      {label}
      {required ? (
        <span className="ml-1 text-[var(--yak-brand-color)]">*</span>
      ) : null}
    </div>
  );
}

const labelFor = (key: string) =>
  key
    .replace(/[._-]+/g, ' ')
    .replace(/\b\w/g, (value) => value.toUpperCase());

const optionValues = (value: unknown): Array<string | number> => {
  if (!Array.isArray(value)) return [];
  return value.filter(
    (item): item is string | number =>
      typeof item === 'string' || typeof item === 'number',
  );
};

const currentValue = (
  field: ConnectorTaskOptionField,
  values: Record<string, any>,
) => {
  const { option } = field;
  return values[option.key] !== undefined
    ? values[option.key]
    : option.defaultValue;
};

export default function ConnectorExtraParameters({
  role,
  schema,
  loading,
  error,
  config,
  onChange,
}: ConnectorExtraParametersProps) {
  if (loading) {
    return (
      <div className="flex items-center gap-2 rounded-lg bg-[#f7f8fa] px-3 py-2.5 text-[11px] text-[#667085]">
        <Spin size="small" />
        正在读取 Connector 扩展参数
      </div>
    );
  }

  if (error) {
    return (
      <Alert
        type="warning"
        showIcon
        message="Connector 扩展参数暂不可用"
        description={error}
      />
    );
  }

  const fields = connectorTaskOptionFields(schema, role);
  if (fields.length === 0) return null;

  const values =
    config.connectorOptions && typeof config.connectorOptions === 'object'
      ? config.connectorOptions
      : {};

  const update = (key: string, value: unknown) => {
    const next = { ...values };
    const empty =
      value === undefined ||
      value === null ||
      value === '' ||
      (Array.isArray(value) && value.length === 0);

    if (empty) {
      delete next[key];
    } else {
      next[key] = value;
    }

    onChange({ connectorOptions: next });
  };

  const renderField = (field: ConnectorTaskOptionField): ReactNode => {
    const { option, kind } = field;
    const value = currentValue(field, values);
    const allowedValues = optionValues(option.allowedValues);

    if (kind === 'boolean') {
      return (
        <Switch
          checked={Boolean(value)}
          onChange={(checked) => update(option.key, checked)}
        />
      );
    }

    if (kind === 'number') {
      return (
        <InputNumber
          variant="filled"
          className="!w-full"
          value={
            value === undefined || value === null || value === ''
              ? undefined
              : Number(value)
          }
          onChange={(next) => update(option.key, next)}
        />
      );
    }

    if (kind === 'enum') {
      return (
        <Select
          allowClear={!option.required}
          variant="filled"
          className="w-full"
          value={value as string | number | undefined}
          options={allowedValues.map((item) => ({
            label: String(item),
            value: item,
          }))}
          onChange={(next) => update(option.key, next)}
        />
      );
    }

    if (kind === 'tags') {
      const selected = Array.isArray(value)
        ? value.map(String)
        : value === undefined || value === null || value === ''
          ? []
          : [String(value)];
      const options = allowedValues.map((item) => ({
        label: String(item),
        value: String(item),
      }));

      return (
        <Select
          mode="tags"
          allowClear
          variant="filled"
          className="w-full"
          value={selected}
          options={options}
          onChange={(next) => update(option.key, next)}
        />
      );
    }

    return (
      <Input
        variant="filled"
        value={value === undefined || value === null ? '' : String(value)}
        placeholder={option.description || `请输入 ${option.key}`}
        onChange={(event) => update(option.key, event.target.value)}
      />
    );
  };

  return (
    <div className="rounded-lg border border-[#eaecf0] bg-white p-3.5">
      <div className="mb-3">
        <div className="text-[12px] font-semibold text-[#344054]">
          Connector 扩展参数
        </div>
        <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
          来自当前 Link-Up Worker 的任务级 Schema；连接和凭据字段由数据源管理。
        </div>
      </div>

      <div className="space-y-3.5">
        {fields.map((field) => (
          <div key={field.option.key}>
            <FieldLabel
              label={labelFor(field.option.key)}
              required={field.option.required === true}
            />
            {renderField(field)}
            {field.option.description ? (
              <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
                {field.option.description}
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </div>
  );
}
