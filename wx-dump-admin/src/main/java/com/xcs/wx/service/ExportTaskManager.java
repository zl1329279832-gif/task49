package com.xcs.wx.service;

import com.xcs.wx.constant.ExportTaskStatus;
import com.xcs.wx.domain.ExportTask;
import com.xcs.wx.domain.dto.ExportTaskCreateDTO;
import com.xcs.wx.domain.vo.ExportTaskVO;

import java.util.List;

/**
 * 导出任务管理器接口
 *
 * @author wx-dump-4j
 */
public interface ExportTaskManager {

    /**
     * 创建导出任务
     *
     * @param dto 创建参数
     * @return 任务ID
     */
    String createTask(ExportTaskCreateDTO dto);

    /**
     * 获取任务详情
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    ExportTask getTask(String taskId);

    /**
     * 获取任务VO（包含downloadToken和expiresIn计算）
     *
     * @param taskId 任务ID
     * @return ExportTaskVO
     */
    ExportTaskVO getTaskVO(String taskId);

    /**
     * 获取指定用户的所有任务VO列表
     *
     * @param wxId 微信账号
     * @return 任务列表
     */
    List<ExportTaskVO> getTasksByUser(String wxId);

    /**
     * 更新任务状态（带状态机校验）
     *
     * @param taskId    任务ID
     * @param newStatus 新状态
     * @throws com.xcs.wx.exception.BizException 状态转换不合法时
     */
    void updateStatus(String taskId, ExportTaskStatus newStatus);

    /**
     * 请求取消任务
     *
     * @param taskId 任务ID
     */
    void requestCancel(String taskId);

    /**
     * 重试失败/取消的任务
     *
     * @param taskId 任务ID
     */
    void retryTask(String taskId);

    /**
     * 生成下载令牌
     *
     * @param taskId 任务ID
     * @return 令牌字符串
     */
    String generateDownloadToken(String taskId);

    /**
     * 清理过期任务及其文件
     */
    void cleanupExpiredTasks();
}
