import { Form, Input, Modal, Select } from 'antd';

import CustomTemplateDrawer from '../CustomTemplateDrawer';
import type { useQualityTemplateLibrary } from '../hooks/useQualityTemplateLibrary';
import { flattenTemplateFolders } from '../utils';

type TemplateLibraryModel = ReturnType<typeof useQualityTemplateLibrary>;

interface TemplateLibraryDialogsProps {
  library: TemplateLibraryModel;
}

export default function TemplateLibraryDialogs({
  library,
}: TemplateLibraryDialogsProps) {
  const {
    folders,
    drawerOpen,
    setDrawerOpen,
    drawerMode,
    editingTemplate,
    submitting,
    folderDialogOpen,
    setFolderDialogOpen,
    folderDialogMode,
    editingFolder,
    copyTemplate,
    setCopyTemplate,
    folderForm,
    copyForm,
    selectedFolderId,
    saveFolder,
    saveTemplate,
    saveCopy,
  } = library;

  return (
    <>
      <CustomTemplateDrawer
        open={drawerOpen}
        mode={drawerMode}
        template={editingTemplate}
        folders={folders}
        defaultFolderId={selectedFolderId}
        submitting={submitting}
        onClose={() => setDrawerOpen(false)}
        onSubmit={saveTemplate}
      />

      <Modal
        title={folderDialogMode === 'create' ? '新建模板目录' : '编辑模板目录'}
        open={folderDialogOpen}
        confirmLoading={submitting}
        onOk={() => void saveFolder()}
        onCancel={() => setFolderDialogOpen(false)}
        destroyOnClose
      >
        <Form form={folderForm} layout="vertical" className="pt-3">
          <Form.Item
            name="name"
            label="目录名称"
            rules={[
              {
                required: true,
                whitespace: true,
                message: '请输入目录名称',
              },
            ]}
          >
            <Input
              variant="filled"
              maxLength={100}
              placeholder="请输入目录名称"
            />
          </Form.Item>
          <Form.Item name="parentId" label="上级目录">
            <Select
              allowClear
              variant="filled"
              placeholder="根目录"
              options={flattenTemplateFolders(folders)
                .filter((folder) => folder.id !== editingFolder?.id)
                .map((folder) => ({
                  value: folder.id,
                  label: `${'　'.repeat(folder.depth)}${folder.name}`,
                }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="复制自定义规则模板"
        open={Boolean(copyTemplate)}
        confirmLoading={submitting}
        onOk={() => void saveCopy()}
        onCancel={() => setCopyTemplate(undefined)}
        destroyOnClose
      >
        <Form form={copyForm} layout="vertical" className="pt-3">
          <Form.Item
            name="name"
            label="模板名称"
            rules={[
              {
                required: true,
                whitespace: true,
                message: '请输入模板名称',
              },
            ]}
          >
            <Input variant="filled" maxLength={100} />
          </Form.Item>
          <Form.Item name="folderId" label="目标文件夹">
            <Select
              allowClear
              variant="filled"
              placeholder="未分类"
              options={flattenTemplateFolders(folders).map((folder) => ({
                value: folder.id,
                label: `${'　'.repeat(folder.depth)}${folder.name}`,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
