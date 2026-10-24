import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, PageHeader, toast } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";

import { useWorkspace } from "@/hooks/use-workspace";
import { WorkspaceTable } from "./workspace-table";
import WorkspaceForm from "./workspace-form";
import { MemberDialog } from "./members/member-dialog";
import { useWorkspaces, workspaceQueryKeys } from "./use-workspaces";

export function WorkspaceManagementPage() {
  const queryClient = useQueryClient();
  const { refreshWorkspaces } = useWorkspace();
  const query = useWorkspaces();
  const records = query.data ?? [];
  const [formOpen, setFormOpen] = useState(false);
  const [memberWorkspaceId, setMemberWorkspaceId] = useState<string>();
  const memberWorkspace = records.find((workspace) => workspace.id === memberWorkspaceId);
  const refresh = () => queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.all });

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader title="工作空间管理" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <section className="flex shrink-0 items-center">
              <Button size="small" variant="primary" onClick={() => setFormOpen(true)}>
                <Plus size={14} />
                新建工作空间
              </Button>
            </section>
            <section className="mt-4 min-h-0 flex-1">
              {query.isError ? (
                <div
                  role="alert"
                  className="mb-3 flex items-center gap-3 text-sm text-[var(--yak-components-muted-text)]"
                >
                  工作空间加载失败{" "}
                  <Button size="small" onClick={() => void query.refetch()}>
                    重试
                  </Button>
                </div>
              ) : null}
              <WorkspaceTable
                records={records}
                loading={query.isPending || query.isFetching}
                onMembers={(workspace) => setMemberWorkspaceId(workspace.id)}
              />
            </section>
          </div>
        </div>
      </div>
      <WorkspaceForm
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          toast.success("工作空间创建成功");
          void Promise.all([refresh(), refreshWorkspaces()]);
        }}
      />
      {memberWorkspace ? (
        <MemberDialog
          key={memberWorkspace.id}
          workspace={memberWorkspace}
          onClose={() => setMemberWorkspaceId(undefined)}
          onRefresh={refresh}
        />
      ) : null}
    </>
  );
}
