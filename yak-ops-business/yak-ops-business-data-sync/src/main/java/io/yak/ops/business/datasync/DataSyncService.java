package io.yak.ops.business.datasync;

import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.page.PagingData;

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

    boolean deleteTask(String id);

    DataSyncInstanceVO queryInstance(String id);

    PagingData<DataSyncInstanceVO> queryInstancePage(DataSyncInstanceQueryDTO dto);
}
