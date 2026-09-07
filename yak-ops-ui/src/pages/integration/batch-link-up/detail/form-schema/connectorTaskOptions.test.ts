import type { LinkUpConnectorSchema } from '@/services/batch-link-up';

import {
  connectorTaskOptionFields,
  validateRequiredConnectorTaskOptions,
} from './connectorTaskOptions';

const schema = (): LinkUpConnectorSchema => ({
  connectorId: 'demo',
  role: 'SOURCE',
  options: [
    {
      key: 'url',
      valueType: 'STRING',
      required: true,
      description: 'Datasource-owned URL',
    },
    {
      key: 'password',
      valueType: 'STRING',
      required: true,
      sensitive: true,
    },
    {
      key: 'table_path',
      valueType: 'STRING',
      required: true,
    },
    {
      key: 'partition_column',
      valueType: 'STRING',
      required: false,
      description: 'Optional split column',
    },
    {
      key: 'partition_num',
      valueType: 'INT',
      defaultValue: 4,
      required: true,
    },
    {
      key: 'required_task_flag',
      valueType: 'STRING',
      required: true,
      fallbackKeys: ['legacy_task_flag'],
    },
    {
      key: 'complex_object',
      valueType: 'MAP',
      required: false,
    },
  ],
});

describe('connectorTaskOptions', () => {
  it('excludes datasource-owned, sensitive, product-managed and unsupported complex options', () => {
    const fields = connectorTaskOptionFields(schema(), 'SOURCE');

    expect(fields.map((item) => item.option.key)).toEqual([
      'partition_column',
      'partition_num',
      'required_task_flag',
    ]);
  });

  it('filters native keys already owned by Yak Ops single-table controls', () => {
    const nativeSchema: LinkUpConnectorSchema = {
      connectorId: 'doris',
      role: 'SINK',
      options: [
        { key: 'table', valueType: 'STRING' },
        { key: 'sink.key-type', valueType: 'STRING' },
        { key: 'doris.batch.size', valueType: 'INT' },
        { key: 'sink.enable-2pc', valueType: 'BOOLEAN' },
      ],
    };

    expect(
      connectorTaskOptionFields(nativeSchema, 'SINK').map(
        (item) => item.option.key,
      ),
    ).toEqual(['sink.enable-2pc']);
  });

  it('validates only visible task-owned required options', () => {
    const errors = validateRequiredConnectorTaskOptions(
      schema(),
      'SOURCE',
      { connectorOptions: {} },
    );

    expect(errors).toEqual([
      'Source Connector 参数 required_task_flag 不能为空',
    ]);
  });

  it('accepts a fallback key and ignores required options with defaults', () => {
    const errors = validateRequiredConnectorTaskOptions(
      schema(),
      'SOURCE',
      {
        connectorOptions: {
          legacy_task_flag: 'enabled',
        },
      },
    );

    expect(errors).toEqual([]);
  });
});
