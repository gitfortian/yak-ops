package io.yak.ops.common.bean.vo.modeling;

import java.util.List;

/**
 * One observed physical table inside a metadata snapshot.
 *
 * @param database observed database
 * @param schema observed schema, null where the engine has none
 * @param name observed table name
 * @param type observed table type reported by the driver
 * @param remarks observed table comment
 * @param fields observed columns in ordinal order
 */
public record MetadataTableVO(
        String database, String schema, String name, String type, String remarks, List<MetadataFieldVO> fields) {}
