import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Form, Input } from 'antd';
import type { Rule } from 'antd/es/form';

const CustomKVList = ({ intl, field }: any) => {
  const form = Form.useFormInstance();
  const maxRows = field?.maxRows ?? 50;

  const keyRules = (currentIndex: number): Rule[] => [
    { required: true, message: '请输入参数名' },
    {
      validator: async (_rule, value) => {
        const normalized = String(value ?? '').trim();
        if (normalized.length > 128) {
          throw new Error('参数名不能超过 128 个字符');
        }
        if (!normalized) return;

        const rows = form.getFieldValue(field.key) || [];
        const duplicated = rows.some(
          (row: any, index: number) =>
            index !== currentIndex &&
            String(row?.key ?? '').trim() === normalized,
        );
        if (duplicated) {
          throw new Error('参数名不能重复');
        }
      },
    },
  ];

  const valueRules: Rule[] = [
    {
      validator: async (_rule, value) => {
        if (value !== undefined && value !== null && String(value).length > 1024) {
          throw new Error('参数值不能超过 1024 个字符');
        }
      },
    },
  ];

  return (
    <div className="mb-3">
      <div className="mb-2">
        <div className="text-[13px] font-medium leading-5 text-[#344054]">
          {field.label}
        </div>
        {field.placeholder && (
          <div className="mt-0.5 text-[11px] leading-4 text-[#98a2b3]">
            {field.placeholder}
          </div>
        )}
      </div>

      <Form.List name={field.key}>
        {(fields, { add, remove }) => {
          const canAdd = fields.length < maxRows;
          return (
            <div className="overflow-hidden rounded-lg border border-[#e7e9ed] bg-white">
              <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_36px] items-center gap-2 border-b border-[#eef0f3] bg-[#fafbfc] px-3 py-2 text-[11px] font-medium text-[#667085]">
                <span>参数名</span>
                <span>参数值</span>
                <span />
              </div>

              {fields.length > 0 ? (
                <div className="divide-y divide-[#f0f1f3]">
                  {fields.map(({ key, name, ...restField }) => (
                    <div
                      key={key}
                      className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_36px] items-start gap-2 px-3 py-2"
                    >
                      <Form.Item
                        {...restField}
                        name={[name, 'key']}
                        rules={keyRules(name)}
                        className="!mb-0"
                      >
                        <Input variant="filled" placeholder="例如：useSSL" />
                      </Form.Item>

                      <Form.Item
                        {...restField}
                        name={[name, 'value']}
                        rules={valueRules}
                        className="!mb-0"
                      >
                        <Input variant="filled" placeholder="例如：false" />
                      </Form.Item>

                      <Button
                        type="text"
                        size="small"
                        danger
                        className="!h-8 !w-8 !px-0"
                        icon={<DeleteOutlined />}
                        aria-label="删除扩展参数"
                        onClick={() => remove(name)}
                      />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="px-3 py-5 text-center text-xs text-[#98a2b3]">
                  暂无扩展参数
                </div>
              )}

              <div className="flex justify-end border-t border-[#eef0f3] bg-[#fcfcfd] px-3 py-2">
                <Button
                  type="text"
                  size="small"
                  icon={<PlusOutlined />}
                  disabled={!canAdd}
                  onClick={() => add({ key: '', value: '' })}
                >
                  {canAdd
                    ? intl.formatMessage({
                        id: 'pages.datasource.form.other.addConnSetting',
                        defaultMessage: '新增参数',
                      })
                    : `最多 ${maxRows} 条`}
                </Button>
              </div>
            </div>
          );
        }}
      </Form.List>
    </div>
  );
};

export default CustomKVList;
