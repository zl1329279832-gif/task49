package com.xcs.wx.service.impl;

import cn.hutool.core.io.FileUtil;
import com.xcs.wx.constant.ExportTaskStatus;
import com.xcs.wx.domain.ExportTask;
import com.xcs.wx.domain.dto.ExportTaskCreateDTO;
import com.xcs.wx.domain.vo.ExportTaskVO;
import com.xcs.wx.exception.BizException;
import com.xcs.wx.service.DownloadTokenService;
import com.xcs.wx.service.ExportTaskManager;
import com.xcs.wx.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 导出任务管理器实现
 *
 * @author wx-dump-4j
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskManagerImpl implements ExportTaskManager {

    private final DownloadTokenService downloadTokenService;
    private final UserService userService;

    /**
     * 任务存储
     */
    private final ConcurrentHashMap<String, ExportTask> taskStore = new ConcurrentHashMap<>();

    /**
     * 已完成任务保留时间：24小时
     */
    private static final long TASK_RETENTION_MS = 24 * 60 * 60 * 1000L;

    @Override
    public String createTask(ExportTaskCreateDTO dto) {
        // 如果未指定wxId，使用当前用户
        if (dto.getWxId() == null || dto.getWxId().trim().isEmpty()) {
            dto.setWxId(userService.currentUser());
        }
        // 默认格式为xlsx
        if (dto.getFormat() == null || dto.getFormat().trim().isEmpty()) {
            dto.setFormat("xlsx");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        ExportTask task = new ExportTask();
        task.setTaskId(taskId);
        task.setWxId(dto.getWxId());
        task.setParams(dto);
        task.setStatus(ExportTaskStatus.PENDING);
        task.setProgress(0);
        task.setProcessedMessages(0);
        task.setTotalMessages(0);
        task.setCreateTime(System.currentTimeMillis());
        task.setCancelRequested(false);

        // 生成文件名
        String fileName = generateFileName(dto);
        task.setFileName(fileName);

        taskStore.put(taskId, task);
        log.info("Created export task [{}] for wxId=[{}], talkers={}", taskId, dto.getWxId(), dto.getTalkers());
        return taskId;
    }

    @Override
    public ExportTask getTask(String taskId) {
        ExportTask task = taskStore.get(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在: " + taskId);
        }
        return task;
    }

    @Override
    public ExportTaskVO getTaskVO(String taskId) {
        return convertToVO(getTask(taskId));
    }

    @Override
    public List<ExportTaskVO> getTasksByUser(String wxId) {
        List<ExportTask> tasks = taskStore.values().stream()
                .filter(t -> wxId == null || wxId.equals(t.getWxId()))
                .sorted(Comparator.comparing(ExportTask::getCreateTime).reversed())
                .collect(Collectors.toList());

        return tasks.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public void updateStatus(String taskId, ExportTaskStatus newStatus) {
        ExportTask task = getTask(taskId);
        ExportTaskStatus currentStatus = task.getStatus();

        if (!ExportTaskStatus.isValidTransition(currentStatus, newStatus)) {
            throw new BizException(400, String.format("不允许从状态[%s]转换到[%s]", currentStatus, newStatus));
        }

        task.setStatus(newStatus);
        if (newStatus == ExportTaskStatus.COMPLETED
                || newStatus == ExportTaskStatus.FAILED
                || newStatus == ExportTaskStatus.CANCELLED) {
            task.setFinishTime(System.currentTimeMillis());
        }
        log.info("Task [{}] status changed: {} -> {}", taskId, currentStatus, newStatus);
    }

    @Override
    public void requestCancel(String taskId) {
        ExportTask task = getTask(taskId);
        ExportTaskStatus status = task.getStatus();

        if (status == ExportTaskStatus.COMPLETED) {
            throw new BizException(400, "已完成的任务不能取消");
        }
        if (status == ExportTaskStatus.CANCELLED) {
            throw new BizException(400, "任务已经是取消状态");
        }

        // 如果是PENDING状态，直接标记为CANCELLED
        if (status == ExportTaskStatus.PENDING) {
            task.markCancelled();
            log.info("Task [{}] cancelled directly (was PENDING)", taskId);
        } else {
            // RUNNING状态，线程安全地设置取消标志
            if (!task.trySetCancelRequested()) {
                throw new BizException(400, "已完成的任务不能取消");
            }
            log.info("Task [{}] cancel requested (RUNNING)", taskId);
        }
    }

    @Override
    public void retryTask(String taskId) {
        ExportTask task = getTask(taskId);
        ExportTaskStatus status = task.getStatus();

        if (status != ExportTaskStatus.FAILED && status != ExportTaskStatus.CANCELLED) {
            throw new BizException(400, "只有失败或取消的任务才能重试");
        }

        // 重置任务状态
        task.setStatus(ExportTaskStatus.PENDING);
        task.setProgress(0);
        task.setProcessedMessages(0);
        task.setTotalMessages(0);
        task.setErrorMessage(null);
        task.setFinishTime(null);
        task.setCancelRequested(false);
        task.setDownloadToken(null);
        task.setDownloadTokenExpireAt(null);

        // 清理旧的导出文件
        if (task.getFilePath() != null) {
            File oldFile = new File(task.getFilePath());
            if (oldFile.exists()) {
                FileUtil.del(oldFile);
            }
            task.setFilePath(null);
        }

        log.info("Task [{}] reset for retry", taskId);
    }

    @Override
    public String generateDownloadToken(String taskId) {
        ExportTask task = getTask(taskId);
        if (task.getStatus() != ExportTaskStatus.COMPLETED) {
            throw new BizException(400, "只有已完成的任务才能下载");
        }
        String token = downloadTokenService.generateToken(taskId);
        task.setDownloadToken(token);
        task.setDownloadTokenExpireAt(System.currentTimeMillis() + 30 * 60 * 1000L);
        return token;
    }

    @Override
    @Scheduled(fixedRate = 1800000) // 每30分钟
    public void cleanupExpiredTasks() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        taskStore.forEach((taskId, task) -> {
            ExportTaskStatus status = task.getStatus();
            boolean isTerminal = (status == ExportTaskStatus.COMPLETED
                    || status == ExportTaskStatus.FAILED
                    || status == ExportTaskStatus.CANCELLED);

            if (isTerminal && task.getFinishTime() != null
                    && (now - task.getFinishTime()) > TASK_RETENTION_MS) {
                toRemove.add(taskId);

                // 清理导出文件
                if (task.getFilePath() != null) {
                    File file = new File(task.getFilePath());
                    if (file.exists()) {
                        if (FileUtil.del(file)) {
                            log.info("Deleted expired export file: {}", task.getFilePath());
                        }
                    }
                }

                // 清理下载令牌
                if (task.getDownloadToken() != null) {
                    downloadTokenService.removeToken(task.getDownloadToken());
                }
            }
        });

        for (String taskId : toRemove) {
            taskStore.remove(taskId);
        }

        if (!toRemove.isEmpty()) {
            log.info("Cleaned up {} expired export tasks", toRemove.size());
        }

        // 同时清理过期令牌
        downloadTokenService.cleanupExpiredTokens();
    }

    /**
     * 转换为 VO
     */
    private ExportTaskVO convertToVO(ExportTask task) {
        ExportTaskVO vo = new ExportTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setWxId(task.getWxId());
        vo.setStatus(task.getStatus().name());
        vo.setProgress(task.getProgress());
        vo.setTotalMessages(task.getTotalMessages());
        vo.setProcessedMessages(task.getProcessedMessages());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreateTime(task.getCreateTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setFileName(task.getFileName());

        // 仅COMPLETED状态返回下载信息
        if (task.getStatus() == ExportTaskStatus.COMPLETED && task.getDownloadToken() != null) {
            vo.setDownloadToken(task.getDownloadToken());
            if (task.getDownloadTokenExpireAt() != null) {
                long remaining = (task.getDownloadTokenExpireAt() - System.currentTimeMillis()) / 1000;
                vo.setExpiresIn(Math.max(0, remaining));
            }
        }

        return vo;
    }

    /**
     * 生成导出文件名
     */
    private String generateFileName(ExportTaskCreateDTO dto) {
        List<String> talkers = dto.getTalkers();
        String baseName;
        if (talkers.size() == 1) {
            baseName = sanitizeFileName(talkers.get(0));
        } else {
            baseName = "批量导出_" + talkers.size() + "个会话";
        }
        // 加上时间戳避免重名
        String timestamp = cn.hutool.core.date.DateUtil.format(new java.util.Date(), "yyyyMMdd_HHmmss");
        return baseName + "_" + timestamp + ".xlsx";
    }

    /**
     * 清理文件名中的非法字符
     */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
