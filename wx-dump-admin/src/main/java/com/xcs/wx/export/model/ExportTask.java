package com.xcs.wx.export.model;

import com.xcs.wx.export.enums.ExportTaskStatus;
import com.xcs.wx.export.enums.ExportType;
import lombok.Data;

import java.util.List;

/**
 * 导出任务实体（内存态）
 *
 * @author xcs
 */
@Data
public class ExportTask {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 微信账号ID（创建时捕获，用于数据源隔离）
     */
    private String wxId;

    /**
     * 导出类型
     */
    private ExportType exportType;

    /**
     * 任务状态
     */
    private volatile ExportTaskStatus status;

    /**
     * 会话ID（MSG导出时必填）
     */
    private String talker;

    /**
     * 会话昵称
     */
    private String talkerNickname;

    /**
     * 起始时间（epoch秒，可选）
     */
    private Long startTime;

    /**
     * 结束时间（epoch秒，可选）
     */
    private Long endTime;

    /**
     * 消息类型过滤（可选，null表示全部）
     */
    private List<Integer> msgTypes;

    /**
     * 是否包含媒体文件
     */
    private boolean includeMedia;

    /**
     * 进度百分比 0-100
     */
    private volatile int progress;

    /**
     * 导出文件路径（完成后设置）
     */
    private String filePath;

    /**
     * 错误信息（失败时设置）
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private long createdAt;

    /**
     * 更新时间
     */
    private long updatedAt;

    /**
     * 完成时间
     */
    private long completedAt;

    /**
     * 协作取消标志
     */
    private volatile boolean cancelRequested;
}
