package io.yak.ops.common.bean.vo.datasync;

import java.util.List;
import lombok.Data;

/**
 * Runtime Trace Cursor 分页结果。
 *
 * @param <T> Trace 记录类型
 * @author weifuwan
 * @since 2026-10-03
 */
@Data
public class DataSyncTracePageVO<T> {

    /** 当前页记录。 */
    private List<T> records;

    /** 下一页 Cursor；无后续数据时为空。 */
    private String nextCursor;

    /** 是否存在后续匹配记录。 */
    private Boolean hasMore;
}
