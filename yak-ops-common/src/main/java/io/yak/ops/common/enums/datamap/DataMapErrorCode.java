package io.yak.ops.common.enums.datamap;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Localized failures owned by the data map.
 *
 * <p>Unknown assets, runs and tags reuse {@code RESOURCE_NOT_EXISTS}; field lengths are enforced
 * by Bean Validation instead of a coded error.
 */
@Getter
@RequiredArgsConstructor
public enum DataMapErrorCode implements ErrorCode {
    /** The datasource, database or schema cannot be resolved for this Workspace. */
    CRAWL_SCOPE_INVALID(19701, "采集范围无法解析，请确认数据源、数据库和 Schema"),
    /** A run already holds the datasource, or the executor queue is full. */
    CRAWL_BUSY(19702, "该数据源已有进行中的采集任务，或采集队列已满，请稍后重试"),
    /** The namespace reaches the two-thousand-table ceiling. */
    CRAWL_TOO_LARGE(19703, "该命名空间表数量达到 2000，已拒绝本次采集"),
    /** The datasource is declared PROD. */
    PREVIEW_FORBIDDEN(19704, "生产环境数据源不允许数据预览"),
    /** The sample exceeded the five-second statement timeout. */
    PREVIEW_TIMEOUT(19705, "数据预览查询超时"),
    /** The chosen domain does not belong to this Workspace. */
    INVALID_DOMAIN(19706, "所选数据域不属于当前工作空间"),
    /** The chosen owner is not an active Workspace member. */
    INVALID_OWNER(19707, "所选负责人不是当前工作空间的有效成员"),
    /** The tag name is blank or longer than the column allows. */
    INVALID_TAG(19708, "标签名称不能为空且不能超过 64 个字符"),
    /** The tag name already exists in this Workspace. */
    DUPLICATE_TAG(19709, "标签名称已存在"),
    /** The caller exceeded the search rate limit. */
    SEARCH_TOO_FREQUENT(19710, "搜索过于频繁，请稍后再试"),
    /** The node already runs the maximum number of preview queries. */
    PREVIEW_BUSY(19711, "当前预览查询过多，请稍后重试"),
    /** A crawl was submitted without naming the database to enumerate. */
    CRAWL_DATABASE_REQUIRED(19712, "请选择要采集的数据库"),
    /** The exclude pattern is not a compilable regular expression. */
    CRAWL_PATTERN_INVALID(19713, "忽略表的正则表达式无法编译，请检查语法"),
    /** The retry source does not exist, belongs to another Workspace or has no failures. */
    CRAWL_RETRY_INVALID(19714, "重试来源无效或该任务没有失败表");

    private final Integer code;
    private final String message;
}
