package com.xcs.wx.domain;

import com.xcs.wx.constant.ExportTaskStatus;
import com.xcs.wx.domain.dto.ExportTaskCreateDTO;
import lombok.Data;

/**
 * 导出任务实体（内存存储）
 *
 * @author wx-dump-4j
 */
@Data
public class ExportTask {

    /**
     * 任务ID (UUID)
     */
    private String taskId;

    /**
     * 数据源账号 wxId
     */
    private String wxId;

    /**
     * 任务状态
     */
    private volatile ExportTaskStatus status;

    /**
     * 完整创建参数
     */
    private ExportTaskCreateDTO params;

    /**
     * 进度百分比 0-100
     */
    private volatile int progress;

    /**
     * 总消息数
     */
    private volatile int totalMessages;

    /**
     * 已处理消息数
     */
    private volatile int processedMessages;

    /**
     * 导出文件绝对路径
     */
    private String filePath;

    /**
     * 下载token (UUID)
     */
    private String downloadToken;

    /**
     * token过期时间戳(ms)
     */
    private Long downloadTokenExpireAt;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 创建时间戳(ms)
     */
    private Long createTime;

    /**
     * 完成时间戳(ms)
     */
    private Long finishTime;

    /**
     * 取消请求标志
     */
    private volatile boolean cancelRequested;

    /**
     * 导出文件名
     */
    private String fileName;

    /**
     * 增加已处理消息数并更新进度
     *
     * @param count 本批处理的消息数
     */
    public synchronized void addProcessed(int count) {
        this.processedMessages += count;
        if (totalMessages > 0) {
            this.progress = Math.min(99, (int) ((long) processedMessages * 100 / totalMessages));
        }
    }

    /**
     * 标记任务完成
     */
    public synchronized void markCompleted() {
        // 如果取消已被请求，优先尊重取消意图
        if (cancelRequested) {
            this.status = ExportTaskStatus.CANCELLED;
            this.finishTime = System.currentTimeMillis();
            return;
        }
        if (this.status != ExportTaskStatus.RUNNING) {
            return;
        }
        this.progress = 100;
        this.status = ExportTaskStatus.COMPLETED;
        this.finishTime = System.currentTimeMillis();
    }

    /**
     * 标记任务失败
     *
     * @param error 错误信息
     */
    public synchronized void markFailed(String error) {
        // 如果取消已被请求，优先尊重取消意图
        if (cancelRequested) {
            this.status = ExportTaskStatus.CANCELLED;
            this.finishTime = System.currentTimeMillis();
            return;
        }
        if (this.status != ExportTaskStatus.RUNNING) {
            return;
        }
        this.status = ExportTaskStatus.FAILED;
        this.errorMessage = error;
        this.finishTime = System.currentTimeMillis();
    }

    /**
     * 标记任务取消
     */
    public synchronized void markCancelled() {
        if (this.status == ExportTaskStatus.COMPLETED
                || this.status == ExportTaskStatus.FAILED
                || this.status == ExportTaskStatus.CANCELLED) {
            return;
        }
        this.status = ExportTaskStatus.CANCELLED;
        this.finishTime = System.currentTimeMillis();
    }
}
