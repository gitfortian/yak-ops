import { Button, Input, Modal, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Folder, Search } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { FileSuffixIcon } from '@/pages/resource-management/components/FileSuffixIcon';
import type { ResourceId, ResourceItem } from '@/pages/resource-management/types';
import { fetchResourceList } from '@/pages/resource-management/service';

export interface ResourcePickerValue {
  id: ResourceId;
  name: string;
  suffix?: string;
  fileSize?: number;
  version?: number;
  checksum?: string;
}

interface ResourcePickerProps {
  open: boolean;
  /** 限定可选文件后缀，如 ['.jar']。不传则不过滤。 */
  acceptSuffixes?: string[];
  /** 当前已选中的资源 ID，用于高亮显示 */
  selectedId?: ResourceId;
  onCancel: () => void;
  onConfirm: (value: ResourcePickerValue) => void;
}

const formatFileSize = (size?: number) => {
  if (size == null || size === 0) return '—';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
};

export default function ResourcePicker({
  open,
  acceptSuffixes,
  selectedId,
  onCancel,
  onConfirm,
}: ResourcePickerProps) {
  const [items, setItems] = useState<ResourceItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [currentParentId, setCurrentParentId] = useState<ResourceId>(0);
  const [breadcrumbs, setBreadcrumbs] = useState<{ id: ResourceId; name: string }[]>([]);
  const [selectedFile, setSelectedFile] = useState<ResourceItem | null>(null);

  const loadList = useCallback(async (parentId: ResourceId, kw?: string) => {
    setLoading(true);
    try {
      const res = await fetchResourceList(parentId, kw);
      if (res.code === 200 && res.data) {
        setItems(res.data);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    setCurrentParentId(0);
    setBreadcrumbs([]);
    setKeyword('');
    setSelectedFile(null);
    loadList(0);
  }, [open, loadList]);

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (item.nodeType === 'DIRECTORY') return true;
      if (acceptSuffixes && acceptSuffixes.length > 0 && item.suffix) {
        return acceptSuffixes.includes(`.${item.suffix}`);
      }
      return true;
    });
  }, [items, acceptSuffixes]);

  const handleEnterDirectory = (item: ResourceItem) => {
    setCurrentParentId(item.id);
    setBreadcrumbs((prev) => [...prev, { id: item.id, name: item.name }]);
    setKeyword('');
    loadList(item.id);
  };

  const handleBreadcrumbClick = (index: number) => {
    if (index < 0) {
      setCurrentParentId(0);
      setBreadcrumbs([]);
    } else {
      const target = breadcrumbs[index];
      setCurrentParentId(target.id);
      setBreadcrumbs((prev) => prev.slice(0, index + 1));
    }
    setKeyword('');
    loadList(breadcrumbs[index]?.id ?? 0);
  };

  const handleSearch = (value: string) => {
    setKeyword(value);
    loadList(currentParentId, value || undefined);
  };

  const handleConfirm = () => {
    if (!selectedFile) return;
    onConfirm({
      id: selectedFile.id,
      name: selectedFile.name,
      suffix: selectedFile.suffix,
      fileSize: selectedFile.fileSize,
      version: selectedFile.version,
      checksum: selectedFile.checksum,
    });
  };

  const columns: ColumnsType<ResourceItem> = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (name: string, record) => {
        if (record.nodeType === 'DIRECTORY') {
          return (
            <div
              className="flex cursor-pointer items-center gap-2 text-[#344054] hover:text-[#1570ef]"
              onClick={() => handleEnterDirectory(record)}
            >
              <Folder size={16} className="shrink-0 text-[#98a2b3]" />
              <span>{name}</span>
            </div>
          );
        }
        const isSelected = selectedFile?.id === record.id;
        return (
          <div
            className={`flex cursor-pointer items-center gap-2 rounded px-2 py-1 ${isSelected ? 'bg-[#eff8ff] text-[#1570ef]' : 'text-[#344054]'}`}
            onClick={() => setSelectedFile(record)}
          >
            <FileSuffixIcon suffix={record.suffix} size={16} className="shrink-0" />
            <span>{name}</span>
          </div>
        );
      },
    },
    {
      title: '后缀',
      dataIndex: 'suffix',
      key: 'suffix',
      width: 80,
      render: (suffix: string | undefined, record) =>
        record.nodeType === 'FILE' && suffix ? (
          <Tag className="text-[10px]">{suffix}</Tag>
        ) : (
          '—'
        ),
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 90,
      render: (size: number | undefined, record) =>
        record.nodeType === 'FILE' ? formatFileSize(size) : '—',
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 70,
      render: (v: number | undefined, record) =>
        record.nodeType === 'FILE' && v != null ? `v${v}` : '—',
    },
  ];

  const isFileSelected = (record: ResourceItem) =>
    record.nodeType === 'FILE' && selectedFile?.id === record.id;

  return (
    <Modal
      open={open}
      title="选择资源文件"
      width={720}
      okText="确认选择"
      cancelText="取消"
      okButtonProps={{ disabled: !selectedFile }}
      onCancel={onCancel}
      onOk={handleConfirm}
      destroyOnClose
    >
      <div className="mb-3 flex items-center gap-2">
        <Input
          prefix={<Search size={14} className="text-[#98a2b3]" />}
          placeholder="搜索文件名"
          allowClear
          value={keyword}
          onChange={(e) => handleSearch(e.target.value)}
          className="flex-1"
        />
        {acceptSuffixes && acceptSuffixes.length > 0 && (
          <Typography.Text className="shrink-0 text-[11px] text-[#98a2b3]">
            类型：{acceptSuffixes.join(' / ')}
          </Typography.Text>
        )}
      </div>

      {/* Breadcrumb */}
      <div className="mb-2 flex items-center gap-1 text-[12px]">
        <Button
          type="link"
          size="small"
          className="px-1"
          disabled={breadcrumbs.length === 0}
          onClick={() => handleBreadcrumbClick(-1)}
        >
          根目录
        </Button>
        {breadcrumbs.map((bc, index) => (
          <span key={String(bc.id)} className="flex items-center gap-1">
            <span className="text-[#98a2b3]">/</span>
            <Button
              type="link"
              size="small"
              className="px-1"
              disabled={index === breadcrumbs.length - 1}
              onClick={() => handleBreadcrumbClick(index)}
            >
              {bc.name}
            </Button>
          </span>
        ))}
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={filteredItems}
        loading={loading}
        size="small"
        pagination={false}
        scroll={{ y: 360 }}
        rowClassName={(record) =>
          isFileSelected(record) ? 'bg-[#eff8ff]' : ''
        }
        locale={{ emptyText: '当前目录无文件' }}
      />

      {selectedFile && (
        <div className="mt-3 rounded-md border border-[#e4e7ec] bg-[#f9fafb] px-3 py-2 text-[12px] text-[#475467]">
          已选择：<span className="font-medium text-[#344054]">{selectedFile.name}</span>
          {selectedFile.suffix && <span className="ml-2 text-[#98a2b3]">.{selectedFile.suffix}</span>}
          <span className="ml-2 text-[#98a2b3]">{formatFileSize(selectedFile.fileSize)}</span>
          {selectedFile.version != null && (
            <span className="ml-2 text-[#98a2b3]">v{selectedFile.version}</span>
          )}
        </div>
      )}
    </Modal>
  );
}
