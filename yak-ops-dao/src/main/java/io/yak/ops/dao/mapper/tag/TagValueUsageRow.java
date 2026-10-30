package io.yak.ops.dao.mapper.tag;

import lombok.Getter;
import lombok.Setter;

/** One tag value with the number of assets currently bound to it. */
@Getter
@Setter
public class TagValueUsageRow {
    /** Value identity. */
    private String valueId;
    /** Bindings that currently reference the value. */
    private Long usageCount;
}
