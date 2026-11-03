package io.yak.ops.business.modeling.semantic;

import io.yak.ops.common.bean.vo.modeling.SemanticExportCheckVO;
import java.util.List;

/** Result of one semantic export run: the pre-flight checks and the rendered Ossie YAML. */
public record SemanticExportResult(List<SemanticExportCheckVO> checks, String yaml) {}
