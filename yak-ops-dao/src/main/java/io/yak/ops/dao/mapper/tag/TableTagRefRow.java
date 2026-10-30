package io.yak.ops.dao.mapper.tag;

import lombok.Getter;
import lombok.Setter;

/** One tag value shown on an asset, carrying its key name beside the value name. */
@Getter
@Setter
public class TableTagRefRow {
    /** Asset the value is bound to. */
    private String tableId;
    /** Key identity of the dimension. */
    private String keyId;
    /** Key name of the dimension. */
    private String keyName;
    /** Value identity of the term. */
    private String valueId;
    /** Value name of the term. */
    private String valueName;
}
