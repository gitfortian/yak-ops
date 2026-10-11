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
    DefinitionVO create(CreateDefinitionDTO dto, String operatorId);

    DefinitionVO update(String id, UpdateDefinitionDTO dto, String operatorId);

    DefinitionVO query(String id);

    PagingData<DefinitionVO> queryPage(DefinitionQueryDTO dto);

    List<DefinitionVersionVO> queryVersions(String id);

    DefinitionVO publish(String id, String operatorId);

    DefinitionVO unpublish(String id, String operatorId);

    void delete(String id, String operatorId);
}
