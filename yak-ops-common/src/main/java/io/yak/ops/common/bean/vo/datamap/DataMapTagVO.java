package io.yak.ops.common.bean.vo.datamap;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Workspace tag shown on an asset. */
@Getter
@AllArgsConstructor
public class DataMapTagVO {

    /** Tag identity. */
    private final String id;

    /** Tag name. */
    private final String name;
}
