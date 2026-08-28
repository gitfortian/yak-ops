import { Button, Modal } from 'antd';
import { TriangleAlert } from 'lucide-react';

interface UnsavedChangesModalProps {
  open: boolean;
  saving: boolean;
  dirtyNames: string[];
  onSave: () => void | Promise<void>;
  onDiscard: () => void | Promise<void>;
  onCancel: () => void;
}

const UnsavedChangesModal = ({
  open,
  saving,
  dirtyNames,
  onSave,
  onDiscard,
  onCancel,
}: UnsavedChangesModalProps) => {
  const dirtyCount = dirtyNames.length;
  const dirtyName = dirtyNames[0] || '当前编辑器';

  return (
    <Modal
      open={open}
      title={null}
      footer={null}
      width={520}
      centered
      maskClosable={false}
      closable={!saving}
      onCancel={() => {
        if (!saving) onCancel();
      }}
    >
      <div className="flex gap-4 px-1 py-2">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center text-[#f79009]">
          <TriangleAlert size={36} strokeWidth={1.7} />
        </div>

        <div className="min-w-0 flex-1 pt-1">
          <div className="text-[16px] font-medium leading-6 text-[#1f2937]">
            {dirtyCount <= 1 ? (
              <>
                是否要保存对 <span className="font-semibold">{dirtyName}</span>{' '}
                的更改？
              </>
            ) : (
              <>是否要保存 {dirtyCount} 个已修改编辑器的更改？</>
            )}
          </div>

          <div className="mt-4 text-[13px] leading-5 text-[#475467]">
            如果不保存，{dirtyCount <= 1 ? '你的更改' : '这些更改'}将丢失。
          </div>

          {dirtyCount > 1 ? (
            <div
              className="mt-2 max-w-[360px] truncate text-[12px] text-[#98a2b3]"
              title={dirtyNames.join('、')}
            >
              {dirtyNames.join('、')}
            </div>
          ) : null}

          <div className="mt-6 flex justify-end gap-2">
            <Button type="primary" loading={saving} onClick={() => void onSave()}>
              {dirtyCount > 1 ? '保存全部' : '保存'}
            </Button>
            <Button disabled={saving} onClick={() => void onDiscard()}>
              {dirtyCount > 1 ? '全部不保存' : '不保存'}
            </Button>
            <Button disabled={saving} onClick={onCancel}>
              取消
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default UnsavedChangesModal;
