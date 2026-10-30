package io.yak.ops.common.bean.vo.datamap;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One tag value as it is shown on an asset.
 *
 * <p>The key travels beside the value because the dictionary is two-level: an asset displays
 * {@code key = value}, and the catalog filter groups selected values by their key.
 */
@Getter
@AllArgsConstructor
public class DataMapTagRefVO {

    /** Key identity of the dimension. */
    private final String keyId;

    /** Key name of the dimension. */
    private final String keyName;

    /** Value identity of the term. */
    private final String valueId;

    /** Value name of the term. */
    private final String valueName;
}
