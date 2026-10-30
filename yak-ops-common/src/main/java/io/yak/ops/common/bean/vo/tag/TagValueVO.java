package io.yak.ops.common.bean.vo.tag;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** One term of a key together with the number of assets currently bound to it. */
@Getter
@AllArgsConstructor
public class TagValueVO {

    /** Value identity. */
    private final String id;

    /** Term name inside its key. */
    private final String name;

    /** Assets currently bound to this value. */
    private final long usageCount;
}
