package io.yak.ops.connector.cdc.mysql.source.assigners;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regression coverage for the Flink CDC source-assigner state transitions. */
class AssignerStatusTest {

    @Test
    void followsUpstreamSnapshotAndNewlyAddedTableTransitions() {
        AssignerStatus status = AssignerStatus.INITIAL_ASSIGNING;
        assertThat(AssignerStatus.isAssigningSnapshotSplits(status)).isTrue();
        status = status.onFinish();
        assertThat(status).isEqualTo(AssignerStatus.INITIAL_ASSIGNING_FINISHED);
        assertThat(AssignerStatus.isAssigningFinished(status)).isTrue();

        status = status.startAssignNewlyTables();
        assertThat(status).isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING);
        status = status.onFinish();
        assertThat(status).isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED);
        assertThat(AssignerStatus.isSnapshotAssigningFinished(status)).isTrue();

        status = status.onBinlogSplitUpdated();
        assertThat(status).isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED);
        assertThat(AssignerStatus.isNewlyAddedAssigningFinished(status)).isTrue();
        assertThat(status.startAssignNewlyTables()).isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING);
    }

    @Test
    void restoresOnlyValidStatusCodesAndRejectsInvalidTransitions() {
        for (AssignerStatus status : AssignerStatus.values()) {
            assertThat(AssignerStatus.fromStatusCode(status.getStatusCode())).isEqualTo(status);
        }
        assertThatThrownBy(() -> AssignerStatus.fromStatusCode(-1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignerStatus.fromStatusCode(5))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignerStatus.INITIAL_ASSIGNING.startAssignNewlyTables())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignerStatus.INITIAL_ASSIGNING_FINISHED.onFinish())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignerStatus.NEWLY_ADDED_ASSIGNING.onBinlogSplitUpdated())
                .isInstanceOf(IllegalStateException.class);
    }
}
