package io.yak.ops.common.bean.vo.modeling;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persisted physical metadata of one publication.
 *
 * @param collectionId snapshot identity
 * @param publicationId publication the snapshot describes
 * @param status {@code SUCCEEDED} or {@code FAILED}; null for an unknown stored value
 * @param tableCount collected table count
 * @param fieldCount collected column count
 * @param error collection failure detail, null on success
 * @param createdTime collection start
 * @param finishedTime collection end, null while running
 * @param tables collected tables with their columns
 */
public record MetadataSnapshotVO(
        String collectionId,
        String publicationId,
        String status,
        int tableCount,
        int fieldCount,
        String error,
        LocalDateTime createdTime,
        LocalDateTime finishedTime,
        List<MetadataTableVO> tables) {}
