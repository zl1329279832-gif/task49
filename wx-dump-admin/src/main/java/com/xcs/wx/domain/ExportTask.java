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
     * 状态锁 —— 保护 status / cancelRequested 的原子性
     */
    private final transient Object stateLock = new Object();

    /**
     * 增加已处理消息数并更新进度
     *
     * @param count 本批处理的消息数
     */
    public void addProcessed(int count) {
        this.processedMessages += count;
        if (totalMessages > 0) {
            this.progress = Math.min(99, (int) ((long) processedMessages * 100 / totalMessages));
        }
    }

    /**
     * 标记任务完成
     */
    public void markCompleted() {
        this.progress = 100;
        this.status = ExportTaskStatus.COMPLETED;
        this.finishTime = System.currentTimeMillis();
    }

    /**
     * 原子地尝试标记完成：若已收到取消请求则返回 false，调用方应转为处理取消。
     *
     * @return true 表示成功标记为 COMPLETED；false 表示取消请求先到达
     */
    public boolean tryMarkCompleted() {
        synchronized (stateLock) {
            if (cancelRequested) {
                return false;
            }
            this.progress = 100;
            this.status = ExportTaskStatus.COMPLETED;
            this.finishTime = System.currentTimeMillis();
            return true;
        }
    }

    /**
     * 线程安全地设置取消标志（RUNNING 状态专用）。
     * 若任务已进入 COMPLETED 状态则拒绝取消。
     *
     * @return true 取消请求已被接受；false 任务已完成，无法取消
     */
    public boolean trySetCancelRequested() {
        synchronized (stateLock) {
            if (status == ExportTaskStatus.COMPLETED) {
                return false;
            }
            this.cancelRequested = true;
            return true;
        }
    }

    /**
     * 标记任务失败
     *
     * @param error 错误信息
     */
    public void markFailed(String error) {
        this.status = ExportTaskStatus.FAILED;
        this.errorMessage = error;
        this.finishTime = System.currentTimeMillis();
    }

    /**
     * 标记任务取消
     */
    public void markCancelled() {
        this.status = ExportTaskStatus.CANCELLED;
        this.finishTime = System.currentTimeMillis();
    }
}
