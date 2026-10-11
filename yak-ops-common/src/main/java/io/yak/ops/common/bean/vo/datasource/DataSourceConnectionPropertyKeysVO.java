package io.yak.ops.common.bean.vo.datasource;

import java.util.List;
import lombok.Data;

/**
 * Exposes advanced connection-property suggestions for a datasource provider.
 */
@Data
public class DataSourceConnectionPropertyKeysVO {

    /** Property names recommended by the provider for the advanced connection editor. */
    private List<String> acceptedPropertyKeys;
}
