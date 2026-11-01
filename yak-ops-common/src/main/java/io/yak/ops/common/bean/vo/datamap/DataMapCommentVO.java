package io.yak.ops.common.bean.vo.datamap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One comment projected for the frontend, with replies nested for top-level rows. */
@Getter
@Setter
public class DataMapCommentVO {

    private String id;

    /** Parent comment id for replies; null for top-level comments. */
    private String parentId;

    /** Plain-text body with @DisplayName tokens. */
    private String body;

    private String authorId;

    private String authorName;

    private Integer replyCount;

    private LocalDateTime createTime;

    /** Nested replies; empty for reply rows themselves. */
    private List<DataMapCommentVO> replies = new ArrayList<>();
}
