package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingPreviewDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingPreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.page.PagingData;
import java.util.List;

/**
 * Data Sync 对上层暴露的唯一稳定 Service Contract。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface DataSyncService {

    DataSyncTaskVO createTask(DataSyncTaskDTO dto);

    DataSyncTaskVO updateTask(String id, DataSyncTaskDTO dto);

    DataSyncTaskVO queryTask(String id);

    PagingData<DataSyncTaskVO> queryTaskPage(DataSyncTaskQueryDTO dto);

    DataSyncMappingPreviewVO previewMapping(DataSyncMappingPreviewDTO dto);

    DataSyncTaskVO publishTask(String id);

    DataSyncTaskVO unpublishTask(String id);

    DataSyncInstanceVO runTask(String id);

    DataSyncScheduleVO saveSchedule(String taskId, DataSyncScheduleDTO dto);

    DataSyncScheduleVO querySchedule(String taskId);

    DataSyncScheduleVO enableSchedule(String taskId);

    DataSyncScheduleVO disableSchedule(String taskId);

    void restoreScheduleRuntime();

    boolean deleteTask(String id);

    DataSyncInstanceVO queryInstance(String id);

    List<DataSyncAttemptVO> queryAttempts(String instanceId);

    PagingData<DataSyncInstanceVO> queryInstancePage(DataSyncInstanceQueryDTO dto);

    DataSyncInstanceVO cancelInstance(String id);
}
