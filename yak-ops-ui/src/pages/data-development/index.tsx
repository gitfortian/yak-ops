import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { BRAND_THEME } from '@/styles/brand';
import { ConfigProvider } from 'antd';

import CreateDevelopmentNodeModal from './components/CreateDevelopmentNodeModal';
import CreateDirectoryModal from './components/CreateDirectoryModal';
import DeleteDevelopmentResourceModal from './components/DeleteDevelopmentResourceModal';
import DevelopmentEditorWorkspace from './components/DevelopmentEditorWorkspace';
import DevelopmentTreePane from './components/DevelopmentTreePane';
import RenameResourceModal from './components/RenameResourceModal';
import { useDataDevelopmentPage } from './hooks/useDataDevelopmentPage';

export default function DataDevelopmentPage() {
  const { currentProject } = useSecurityProject();
  const defaultProjectId = currentProject?.id
    ? String(currentProject.id)
    : undefined;
  const page = useDataDevelopmentPage({ defaultProjectId });

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[640px] flex-col overflow-hidden bg-[#f5f5f6]">
        <div className="flex min-h-0 flex-1 overflow-hidden border border-[#e4e7ec] bg-white">
          <DevelopmentTreePane
            treeData={page.treeData}
            treeLoading={page.treeLoading}
            selectedNodeKey={page.selectedNodeKey}
            searchValue={page.treeKeyword}
            leftWidth={page.treeWidth}
            collapsed={page.treeCollapsed}
            onCreateDirectory={page.openCreateDirectory}
            onCreateNode={page.openCreateNode}
            onResourceAction={page.handleResourceAction}
            onSearchChange={page.setTreeKeyword}
            onResizeStart={page.handleResizeStart}
            onCollapsedChange={page.setTreeCollapsed}
            onSelect={page.selectTreeNodes}
          />

          <DevelopmentEditorWorkspace
            nodes={page.nodes}
            directories={page.directories}
            selectedNodeId={page.selectedResourceNodeId}
            onNodeFocus={page.focusNode}
            onCreateNode={page.openCreateNode}
            onNodesChanged={page.loadTree}
          />
        </div>

        <CreateDevelopmentNodeModal
          open={page.createNodeOpen}
          type={page.createNodeType}
          directories={page.directories}
          loading={page.nodeSaving}
          defaultProjectId={defaultProjectId}
          defaultDirectoryId={page.directoryIdForSelection}
          onCancel={page.closeCreateNode}
          onNext={(type, projectId, directoryId, name) =>
            void page.submitNode(type, projectId, directoryId, name)
          }
        />

        <CreateDirectoryModal
          open={page.createDirectoryOpen}
          directories={page.directories}
          defaultParentId={page.directoryIdForSelection}
          loading={page.directorySaving}
          onCancel={page.closeCreateDirectory}
          onSubmit={(parentId, name) =>
            void page.submitDirectory(parentId, name)
          }
        />

        <RenameResourceModal
          open={Boolean(page.renameTarget)}
          resourceLabel={
            page.renameTarget?.nodeType === 'directory' ? '目录' : '节点'
          }
          initialName={page.renameTarget?.title || ''}
          loading={page.renameSaving}
          onCancel={page.closeRename}
          onSubmit={(name) => void page.submitRename(name)}
        />

        <DeleteDevelopmentResourceModal
          target={page.deleteTarget}
          loading={page.deleteSaving}
          onCancel={page.closeDelete}
          onConfirm={() => void page.submitDelete()}
        />
      </div>
    </ConfigProvider>
  );
}
