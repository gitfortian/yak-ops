package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.enums.modeling.ModelType;

/** One DWD or DWS model that an enabled layer naming rule rejects. */
public record NamingViolationVO(String modelId, String name, String cnName, ModelType type, String processCode) {}
