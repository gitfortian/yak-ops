package io.yak.ops.business.task.definition;

import io.yak.ops.common.bean.dto.task.CreateDefinitionDTO;
import io.yak.ops.common.bean.dto.task.DefinitionQueryDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import io.yak.ops.common.page.PagingData;
import java.util.List;

/** One generic Task Definition business entrypoint, independent of plugin type. */
public interface DefinitionService {
    /** Creates a Workspace-owned definition with its first immutable parameter version. */
    DefinitionVO create(CreateDefinitionDTO dto, String operatorId);

    /** Updates an unpublished definition by appending a new immutable version. */
    DefinitionVO update(String id, UpdateDefinitionDTO dto, String operatorId);

    DefinitionVO query(String id);

    PagingData<DefinitionVO> queryPage(DefinitionQueryDTO dto);

    /** Returns saved versions without reinterpreting historical plugin parameters. */
    List<DefinitionVersionVO> queryVersions(String id);

    /** Publishes the current validated definition version for future task use. */
    DefinitionVO publish(String id, String operatorId);

    /** Prevents new executions without deleting existing immutable versions. */
    DefinitionVO unpublish(String id, String operatorId);

    /** Logically deletes a Workspace-owned definition while preserving version history. */
    void delete(String id, String operatorId);
}
