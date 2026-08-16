import {
  Button,
  Input,
  Modal,
  Select,
  Spin,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import { Copy, Play, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  fetchDataServiceDocumentation,
  fetchDataServiceOpenApi,
  saveDataServiceDocumentation,
  testDataService,
  type DataServiceApi,
  type DataServiceDocumentation,
  type DataServiceParameterDoc,
  type DataServiceQueryResult,
  type DataServiceResponseFieldDoc,
  type DataServiceSchemaType,
} from './service';

interface DataServiceDocsModalProps {
  open: boolean;
  service?: DataServiceApi;
  readOnly?: boolean;
  onCancel: () => void;
}

const parameterTypes: DataServiceSchemaType[] = [
  'STRING', 'INTEGER', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME',
];
const responseTypes: DataServiceSchemaType[] = [...parameterTypes, 'OBJECT'];

const typeLabel: Record<DataServiceSchemaType, string> = {
  STRING: 'String',
  INTEGER: 'Integer',
  NUMBER: 'Number',
  BOOLEAN: 'Boolean',
  DATE: 'Date',
  DATETIME: 'DateTime',
  OBJECT: 'Object',
};

const DataServiceDocsModal = ({ open, service, readOnly = false, onCancel }: DataServiceDocsModalProps) => {
  const [documentation, setDocumentation] = useState<DataServiceDocumentation>();
  const [parameters, setParameters] = useState<DataServiceParameterDoc[]>([]);
  const [responseFields, setResponseFields] = useState<DataServiceResponseFieldDoc[]>([]);
  const [openApi, setOpenApi] = useState<Record<string, unknown>>();
  const [debugValues, setDebugValues] = useState<Record<string, string>>({});
  const [testResult, setTestResult] = useState<DataServiceQueryResult>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);

  const load = useCallback(async () => {
    if (!service) return;
    setLoading(true);
    try {
      const [docResponse, openApiResponse] = await Promise.all([
        fetchDataServiceDocumentation(service.id),
        fetchDataServiceOpenApi(service.id),
      ]);
      const doc = docResponse.data;
      if (!doc) throw new Error(docResponse.message || docResponse.msg || '加载 API 文档失败');
      setDocumentation(doc);
      setParameters(doc.parameters || []);
      setResponseFields(doc.responseFields || []);
      setOpenApi(openApiResponse.data || {});
      setDebugValues((current) => {
        const next: Record<string, string> = {};
        for (const parameter of doc.parameters || []) {
          next[parameter.name] = current[parameter.name] || parameter.example || '';
        }
        return next;
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 API 文档失败');
    } finally {
      setLoading(false);
    }
  }, [service]);

  useEffect(() => {
    if (open) {
      setTestResult(undefined);
      void load();
    }
  }, [load, open]);

  const save = async () => {
    if (!service || readOnly) return;
    setSaving(true);
    try {
      const response = await saveDataServiceDocumentation(service.id, { parameters, responseFields });
      const doc = response.data;
      if (!doc) throw new Error(response.message || response.msg || '保存 API 文档失败');
      setDocumentation(doc);
      setParameters(doc.parameters || []);
      setResponseFields(doc.responseFields || []);
      const openApiResponse = await fetchDataServiceOpenApi(service.id);
      setOpenApi(openApiResponse.data || {});
      message.success('API 文档与 OpenAPI 已更新');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 API 文档失败');
    } finally {
      setSaving(false);
    }
  };

  const runTest = async () => {
    if (!service) return;
    const missing = parameters.find((item) => item.required && !String(debugValues[item.name] || '').trim());
    if (missing) {
      message.warning(`请输入参数 ${missing.name}`);
      return;
    }
    setTesting(true);
    try {
      const response = await testDataService(service.id, debugValues);
      const result = response.data;
      if (!result) throw new Error(response.message || response.msg || '在线调试失败');
      setTestResult(result);
      if (!readOnly) setResponseFields((current) => inferResponseFields(result, current));
      message.success(readOnly ? '调试成功' : '调试成功，已根据真实结果识别响应 Schema；保存文档后生效');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '在线调试失败');
    } finally {
      setTesting(false);
    }
  };

  const copy = async (value: string, success = '已复制') => {
    try {
      await navigator.clipboard.writeText(value);
      message.success(success);
    } catch {
      message.warning('复制失败，请手动复制');
    }
  };

  const curl = useMemo(() => {
    if (!service) return '';
    const lineBreak = ' ' + '\\' + '\n';
    const query = parameters
      .map((item) => `  --data-urlencode '${item.name}=${item.example || `<${item.name}>`}'`)
      .join(lineBreak);
    const auth = service.authMode === 'API_KEY'
      ? `${lineBreak}  -H 'X-API-Key: <your-api-key>'`
      : '';
    return `curl -G '${service.runtimePath}'${query ? `${lineBreak}${query}` : ''}${auth}`;
  }, [parameters, service]);

  const resultColumns = (testResult?.columns || []).map((name) => ({
    title: name,
    dataIndex: name,
    key: name,
    minWidth: 140,
    ellipsis: true,
  }));

  const items = [
    {
      key: 'overview',
      label: '概览',
      children: (
        <div className="space-y-4">
          {readOnly ? (
            <div className="border border-[#e5e7eb] bg-[#fafafa] px-3 py-2.5 text-[11px] leading-5 text-[#667085]">
              当前 API 的名称、Path、参数与响应 Contract 来自数据开发已发布的 Data Service Revision，在 Runtime 中只读。修改后请发布新的 DS Revision 并重新同步 Runtime。
            </div>
          ) : null}
          <div className="border border-[#e5e7eb] bg-[#fafbfc] px-3 py-3">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="text-[11px] text-[#98a2b3]">Endpoint</div>
                <div className="mt-1 break-all font-mono text-[12px] text-[#344054]">GET {service?.runtimePath}</div>
              </div>
              <Button size="small" type="text" icon={<Copy size={14} />} onClick={() => void copy(service?.runtimePath || '')} />
            </div>
          </div>
          <div className="grid grid-cols-4 gap-2">
            <Info label="Method" value="GET" />
            <Info label="Auth" value={service?.authMode === 'API_KEY' ? 'API Key' : 'Public'} />
            <Info label="Max Rows" value={String(service?.maxRows || '-')} />
            <Info label="Timeout" value={`${service?.timeoutSeconds || '-'}s`} />
          </div>
          {documentation?.schemaStale ? (
            <div className="border border-[#fedf89] bg-[#fffaeb] px-3 py-2.5 text-[11px] leading-5 text-[#93370d]">
              当前 Runtime SQL 与已保存 Contract 不一致，请同步最新 Data Service Revision。
            </div>
          ) : null}
          <div>
            <div className="mb-1.5 flex items-center justify-between">
              <span className="text-[12px] font-medium text-[#344054]">cURL</span>
              <Button size="small" type="text" icon={<Copy size={13} />} onClick={() => void copy(curl, 'cURL 已复制')}>复制</Button>
            </div>
            <pre className="m-0 max-h-[180px] overflow-auto bg-[#111827] p-3 text-[11px] leading-5 text-white/85">{curl}</pre>
          </div>
        </div>
      ),
    },
    {
      key: 'parameters',
      label: `参数 ${parameters.length || ''}`,
      children: parameters.length ? (
        <Table
          rowKey="name"
          size="small"
          pagination={false}
          dataSource={parameters}
          columns={[
            { title: '参数名', dataIndex: 'name', width: 150, render: (value) => <span className="font-mono text-[12px]">{value}</span> },
            {
              title: '类型', dataIndex: 'type', width: 130,
              render: (_, row, index) => <Select disabled={readOnly} size="small" className="w-full" value={row.type} options={parameterTypes.map((type) => ({ value: type, label: typeLabel[type] }))} onChange={(type) => setParameters((values) => values.map((item, i) => i === index ? { ...item, type: type as DataServiceParameterDoc['type'] } : item))} />,
            },
            { title: '必填', dataIndex: 'required', width: 70, render: (value) => <Tag bordered={false}>{value ? '是' : '否'}</Tag> },
            {
              title: '说明', dataIndex: 'description', minWidth: 180,
              render: (_, row, index) => <Input disabled={readOnly} size="small" value={row.description || ''} placeholder="业务含义" onChange={(event) => setParameters((values) => values.map((item, i) => i === index ? { ...item, description: event.target.value } : item))} />,
            },
            {
              title: '示例', dataIndex: 'example', width: 160,
              render: (_, row, index) => <Input disabled={readOnly} size="small" value={row.example || ''} placeholder="示例值" onChange={(event) => setParameters((values) => values.map((item, i) => i === index ? { ...item, example: event.target.value } : item))} />,
            },
          ]}
          scroll={{ x: 780 }}
        />
      ) : <EmptyHint text="当前 SQL 没有命名参数。" />,
    },
    {
      key: 'response',
      label: `响应 ${responseFields.length || ''}`,
      children: responseFields.length ? (
        <div className="space-y-3">
          <div className="text-[11px] text-[#667085]">
            {readOnly ? '响应 Contract 来自当前 DS Revision。' : '字段来自最近一次在线调试结果识别；类型和业务说明可以在保存前调整。'}
          </div>
          <Table
            rowKey="name"
            size="small"
            pagination={false}
            dataSource={responseFields}
            columns={[
              { title: '字段', dataIndex: 'name', width: 150, render: (value) => <span className="font-mono text-[12px]">{value}</span> },
              {
                title: '类型', dataIndex: 'type', width: 130,
                render: (_, row, index) => <Select disabled={readOnly} size="small" className="w-full" value={row.type} options={responseTypes.map((type) => ({ value: type, label: typeLabel[type] }))} onChange={(type) => setResponseFields((values) => values.map((item, i) => i === index ? { ...item, type: type as DataServiceSchemaType } : item))} />,
              },
              { title: '可空', dataIndex: 'nullable', width: 70, render: (value) => value ? '是' : '否' },
              {
                title: '说明', dataIndex: 'description', minWidth: 180,
                render: (_, row, index) => <Input disabled={readOnly} size="small" value={row.description || ''} placeholder="字段含义" onChange={(event) => setResponseFields((values) => values.map((item, i) => i === index ? { ...item, description: event.target.value } : item))} />,
              },
              { title: '示例', dataIndex: 'example', width: 160, ellipsis: true },
            ]}
            scroll={{ x: 780 }}
          />
        </div>
      ) : <EmptyHint text={readOnly ? '当前 DS Revision 没有响应 Contract。' : '还没有响应 Schema。进入“在线调试”执行一次真实查询即可自动识别。'} />,
    },
    {
      key: 'debug',
      label: '在线调试',
      children: (
        <div>
          <div className="mb-4 border border-[#e5e7eb] bg-[#fafafa] px-3 py-2 text-[11px] leading-5 text-[#667085]">
            管理控制台调试始终直连当前 Runtime Snapshot 的真实数据源，不读取 Runtime 缓存，也不受熔断状态影响。
          </div>
          {parameters.length ? (
            <div className="mb-3 grid grid-cols-2 gap-x-3">
              {parameters.map((parameter) => (
                <div key={parameter.name} className="mb-3">
                  <div className="mb-1 text-[11px] font-medium text-[#475467]">{parameter.name} <span className="text-[#98a2b3]">· {typeLabel[parameter.type]}</span></div>
                  <Input value={debugValues[parameter.name] || ''} placeholder={parameter.example || `请输入 ${parameter.name}`} onChange={(event) => setDebugValues((current) => ({ ...current, [parameter.name]: event.target.value }))} />
                </div>
              ))}
            </div>
          ) : <div className="mb-3 text-[11px] text-[#98a2b3]">当前接口无请求参数。</div>}
          <Button type="primary" icon={<Play size={14} />} loading={testing} onClick={() => void runTest()}>发送请求</Button>
          {testResult ? (
            <div className="mt-4 border-t border-[#eef0f2] pt-3">
              <div className="mb-2 flex items-center gap-4 text-[11px] text-[#667085]">
                <span>200 OK</span><span>{testResult.durationMs} ms</span><span>{testResult.rowCount} 行</span>{testResult.truncated ? <Tag>已截断</Tag> : null}
              </div>
              <Table rowKey={(_, index) => String(index)} size="small" pagination={false} dataSource={testResult.rows} columns={resultColumns} scroll={{ x: 'max-content', y: 300 }} />
            </div>
          ) : null}
        </div>
      ),
    },
    {
      key: 'openapi',
      label: 'OpenAPI',
      children: (
        <div>
          <div className="mb-2 flex items-center justify-between">
            <div className="text-[11px] text-[#667085]">OpenAPI 3.0.3 · 根据当前 Runtime Contract 与访问控制动态生成。</div>
            <Button size="small" icon={<Copy size={13} />} onClick={() => void copy(JSON.stringify(openApi || {}, null, 2), 'OpenAPI JSON 已复制')}>复制 JSON</Button>
          </div>
          <pre className="m-0 max-h-[430px] overflow-auto bg-[#111827] p-3 text-[11px] leading-5 text-white/85">{JSON.stringify(openApi || {}, null, 2)}</pre>
        </div>
      ),
    },
  ];

  const footer = [
    <Button key="refresh" icon={<RefreshCw size={14} />} disabled={loading || saving} onClick={() => void load()}>刷新</Button>,
    <Button key="close" onClick={onCancel}>关闭</Button>,
  ];
  if (!readOnly) {
    footer.push(<Button key="save" type="primary" loading={saving} onClick={() => void save()}>保存文档</Button>);
  }

  return (
    <Modal
      open={open}
      width={980}
      centered
      destroyOnHidden
      title={service ? `${readOnly ? 'API Contract' : 'API 文档'} · ${service.name}` : 'API 文档'}
      onCancel={onCancel}
      footer={footer}
    >
      <Spin spinning={loading}>
        <div className="min-h-[500px] pt-1">
          <div className="mb-3 flex items-center gap-2 text-[11px] text-[#667085]">
            <Tag bordered={false}>GET</Tag>
            {service?.authMode === 'API_KEY' ? <Tag bordered={false}>API Key</Tag> : <Tag bordered={false}>Public</Tag>}
            {readOnly ? <Tag bordered={false}>DS Revision · 只读</Tag> : null}
            {documentation?.schemaStale ? <Tag color="warning">Schema 待同步</Tag> : documentation?.documented ? <Tag bordered={false}>已同步</Tag> : <Tag bordered={false}>未保存</Tag>}
          </div>
          <Tabs items={items} />
        </div>
      </Spin>
    </Modal>
  );
};

const inferResponseFields = (
  result: DataServiceQueryResult,
  current: DataServiceResponseFieldDoc[],
): DataServiceResponseFieldDoc[] => {
  const existing = new Map(current.map((item) => [item.name, item]));
  return (result.columns || []).map((name) => {
    const values = (result.rows || []).map((row) => row[name]);
    const sample = values.find((value) => value !== null && value !== undefined);
    const previous = existing.get(name);
    return {
      name,
      type: inferType(sample),
      nullable: values.length === 0 || values.some((value) => value === null || value === undefined),
      description: previous?.description || '',
      example: sample === undefined || sample === null ? '' : stringifyExample(sample),
    };
  });
};

const inferType = (value: unknown): DataServiceSchemaType => {
  if (typeof value === 'boolean') return 'BOOLEAN';
  if (typeof value === 'number') return Number.isInteger(value) ? 'INTEGER' : 'NUMBER';
  if (typeof value === 'object' && value !== null) return 'OBJECT';
  if (typeof value === 'string') {
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return 'DATE';
    if (/^\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}/.test(value)) return 'DATETIME';
  }
  return 'STRING';
};

const stringifyExample = (value: unknown) => {
  if (typeof value === 'object') {
    try { return JSON.stringify(value); } catch { return String(value); }
  }
  return String(value);
};

const Info = ({ label, value }: { label: string; value: string }) => (
  <div className="border border-[#e5e7eb] px-3 py-2.5">
    <div className="text-[10px] text-[#98a2b3]">{label}</div>
    <div className="mt-1 text-[12px] font-medium text-[#344054]">{value}</div>
  </div>
);

const EmptyHint = ({ text }: { text: string }) => (
  <div className="border border-dashed border-[#d0d5dd] px-4 py-10 text-center text-[12px] text-[#98a2b3]">{text}</div>
);

export default DataServiceDocsModal;
