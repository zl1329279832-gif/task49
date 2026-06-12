package com.xcs.wx.service.impl;

import cn.hutool.core.io.FileUtil;
import com.xcs.wx.domain.dto.ExportTaskCreateDTO;
import com.xcs.wx.domain.vo.DownloadTokenVO;
import com.xcs.wx.domain.vo.ExportTaskVO;
import com.xcs.wx.exception.BizException;
import com.xcs.wx.export.enums.ExportTaskStatus;
import com.xcs.wx.export.enums.ExportType;
import com.xcs.wx.export.executor.ExportTaskExecutor;
import com.xcs.wx.export.model.ExportTask;
import com.xcs.wx.service.DownloadTokenService;
import com.xcs.wx.service.ExportTaskService;
import com.xcs.wx.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 导出任务服务实现
 *
 * @author xcs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskServiceImpl implements ExportTaskService {

    private final ConcurrentHashMap<String, ExportTask> taskStore = new ConcurrentHashMap<>();
    private final ExportTaskExecutor exportTaskExecutor;
    private final DownloadTokenService downloadTokenService;
    private final UserService userService;

    @Override
    public ExportTaskVO createTask(ExportTaskCreateDTO dto) {
        // 参数校验
        ExportType exportType;
        try {
            exportType = ExportType.valueOf(dto.getExportType());
        } catch (IllegalArgumentException e) {
            throw new BizException(-1, "不支持的导出类型: " + dto.getExportType());
        }
        if (exportType == ExportType.MSG && (dto.getTalker() == null || dto.getTalker().isEmpty())) {
            throw new BizException(-1, "消息导出必须指定会话ID");
        }

        ExportTask task = new ExportTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setWxId(userService.currentUser());
        task.setExportType(exportType);
        task.setStatus(ExportTaskStatus.CREATED);
        task.setTalker(dto.getTalker());
        task.setStartTime(dto.getStartTime());
        task.setEndTime(dto.getEndTime());
        task.setMsgTypes(dto.getMsgTypes());
        task.setIncludeMedia(dto.isIncludeMedia());
        task.setProgress(0);
        task.setCreatedAt(System.currentTimeMillis());
        task.setUpdatedAt(System.currentTimeMillis());

        taskStore.put(task.getTaskId(), task);

        // 转入队列并提交执行
        task.setStatus(ExportTaskStatus.QUEUED);
        exportTaskExecutor.submitTask(task, this::onTaskComplete);

        return toVO(task);
    }

    @Override
    public ExportTaskVO getTask(String taskId) {
        ExportTask task = taskStore.get(taskId);
        if (task == null) {
            throw new BizException(-1, "任务不存在: " + taskId);
        }
        return toVO(task);
    }

    @Override
    public List<ExportTaskVO> listTasks() {
        String currentWxId = userService.currentUser();
        return taskStore.values().stream()
                .filter(t -> t.getWxId().equals(currentWxId))
                .sorted(Comparator.comparingLong(ExportTask::getCreatedAt).reversed())
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public void cancelTask(String taskId) {
        ExportTask task = taskStore.get(taskId);
        if (task == null) {
            throw new BizException(-1, "任务不存在");
        }
        if (!task.getStatus().canTransitionTo(ExportTaskStatus.CANCELLED)) {
            throw new BizException(-1, "当前状态无法取消: " + task.getStatus());
        }
        task.setCancelRequested(true);
        task.setStatus(ExportTaskStatus.CANCELLED);
        task.setUpdatedAt(System.currentTimeMillis());
        cleanupTaskFiles(task);
    }

    @Override
    public ExportTaskVO retryTask(String taskId) {
        ExportTask task = taskStore.get(taskId);
        if (task == null) {
            throw new BizException(-1, "任务不存在");
        }
        if (!task.getStatus().canTransitionTo(ExportTaskStatus.QUEUED)) {
            throw new BizException(-1, "只有失败的任务可以重试");
        }
        task.setStatus(ExportTaskStatus.QUEUED);
        task.setProgress(0);
        task.setErrorMessage(null);
        task.setCancelRequested(false);
        task.setUpdatedAt(System.currentTimeMillis());

        exportTaskExecutor.submitTask(task, this::onTaskComplete);
        return toVO(task);
    }

    @Override
    public DownloadTokenVO generateDownloadToken(String taskId) {
        ExportTask task = taskStore.get(taskId);
        if (task == null || task.getStatus() != ExportTaskStatus.COMPLETED) {
            throw new BizException(-1, "任务未完成，无法生成下载令牌");
        }
        if (task.getFilePath() == null) {
            throw new BizException(-1, "导出文件不存在");
        }
        return downloadTokenService.generateToken(taskId, task.getFilePath());
    }

    @Override
    public void deleteTask(String taskId) {
        ExportTask task = taskStore.remove(taskId);
        if (task != null) {
            cleanupTaskFiles(task);
        }
    }

    @Override
    public void cleanupOldTasks(long cutoffTimestamp) {
        taskStore.entrySet().removeIf(entry -> {
            ExportTask task = entry.getValue();
            boolean isTerminal = task.getStatus() == ExportTaskStatus.COMPLETED
                    || task.getStatus() == ExportTaskStatus.FAILED
                    || task.getStatus() == ExportTaskStatus.CANCELLED;
            if (isTerminal && task.getUpdatedAt() < cutoffTimestamp) {
                cleanupTaskFiles(task);
                return true;
            }
            return false;
        });
    }

    private void onTaskComplete(ExportTask task) {
        log.info("导出任务 {} 完成，状态: {}", task.getTaskId(), task.getStatus());
    }

    private void cleanupTaskFiles(ExportTask task) {
        downloadTokenService.revokeTokensForTask(task.getTaskId());
        if (task.getFilePath() != null) {
            FileUtil.del(task.getFilePath());
        }
    }

    private ExportTaskVO toVO(ExportTask task) {
        ExportTaskVO vo = new ExportTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setWxId(task.getWxId());
        vo.setExportType(task.getExportType().name());
        vo.setStatus(task.getStatus().name());
        vo.setTalker(task.getTalker());
        vo.setTalkerNickname(task.getTalkerNickname());
        vo.setProgress(task.getProgress());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setFileName(task.getFilePath() != null ? new File(task.getFilePath()).getName() : null);
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        vo.setCompletedAt(task.getCompletedAt());
        return vo;
    }
}
