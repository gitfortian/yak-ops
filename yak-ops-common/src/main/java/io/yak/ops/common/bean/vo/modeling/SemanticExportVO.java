package io.yak.ops.common.bean.vo.modeling;

import java.util.List;

/**
 * Export envelope: the pre-flight checks a reviewer reads before downloading, plus the Ossie YAML
 * document itself. One request produces both, so the checks can never drift from the artifact.
 */
public record SemanticExportVO(List<SemanticExportCheckVO> checks, String yaml) {}
