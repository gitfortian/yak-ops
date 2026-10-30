package io.yak.ops.workspace;

import io.yak.ops.common.bean.dto.workspace.WorkspaceDTO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceMemberVO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceVO;
import io.yak.ops.common.enums.workspace.WorkspaceRole;
import java.util.List;

/**
 * 定义 Workspace 创建、发现和成员管理的稳定业务边界。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public interface WorkspaceService {

    WorkspaceVO createWorkspace(WorkspaceDTO dto, String userId);

    List<WorkspaceVO> queryMyWorkspaces(String userId);

    WorkspaceVO queryWorkspace(String workspaceId, String userId);

    List<WorkspaceMemberVO> queryWorkspaceMembers(String workspaceId, String userId);

    WorkspaceMemberVO addWorkspaceMember(
            String workspaceId, String targetUserId, WorkspaceRole role, String operatorUserId);

    WorkspaceMemberVO updateWorkspaceMemberRole(
            String workspaceId, String targetUserId, WorkspaceRole role, String operatorUserId);

    boolean removeWorkspaceMember(String workspaceId, String targetUserId, String operatorUserId);

    boolean isMember(String workspaceId, String userId);

    /**
     * Checks live membership and the application-scoped active account for internal submissions.
     * @param workspaceId target Workspace
     * @param userId recorded authorizing actor
     * @return true only while both membership and account remain active
     */
    boolean isActiveMember(String workspaceId, String userId);

    /**
     * Checks whether the user manages the Workspace, i.e. holds the OWNER or ADMIN role.
     * @param workspaceId target Workspace
     * @param userId user whose role is checked
     * @return true only for a live OWNER or ADMIN membership
     */
    boolean isManager(String workspaceId, String userId);
}
