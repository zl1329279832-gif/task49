package com.xcs.wx.service;

import com.xcs.wx.domain.dto.ExportTaskCreateDTO;
import com.xcs.wx.domain.vo.DownloadTokenVO;
import com.xcs.wx.domain.vo.ExportTaskVO;

import java.util.List;

/**
 * 导出任务服务
 *
 * @author xcs
 */
public interface ExportTaskService {

    /**
     * 创建导出任务
     *
     * @param dto 创建请求
     * @return 任务VO
     */
    ExportTaskVO createTask(ExportTaskCreateDTO dto);

    /**
     * 查询任务详情
     *
     * @param taskId 任务ID
     * @return 任务VO
     */
    ExportTaskVO getTask(String taskId);

    /**
     * 查询当前用户的所有任务
     *
     * @return 任务列表
     */
    List<ExportTaskVO> listTasks();

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     */
    void cancelTask(String taskId);

    /**
     * 重试失败的任务
     *
     * @param taskId 任务ID
     * @return 任务VO
     */
    ExportTaskVO retryTask(String taskId);

    /**
     * 为已完成的任务生成下载令牌
     *
     * @param taskId 任务ID
     * @return 下载令牌VO
     */
    DownloadTokenVO generateDownloadToken(String taskId);

    /**
     * 删除任务及其关联文件
     *
     * @param taskId 任务ID
     */
    void deleteTask(String taskId);

    /**
     * 清理过期任务
     *
     * @param cutoffTimestamp 截止时间
     */
    void cleanupOldTasks(long cutoffTimestamp);
}
