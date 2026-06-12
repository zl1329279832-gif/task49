package com.xcs.wx.domain.vo;

import lombok.Data;

/**
 * 导出任务响应VO
 *
 * @author xcs
 */
@Data
public class ExportTaskVO {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 微信账号ID
     */
    private String wxId;

    /**
     * 导出类型
     */
    private String exportType;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 会话ID
     */
    private String talker;

    /**
     * 会话昵称
     */
    private String talkerNickname;

    /**
     * 进度百分比
     */
    private int progress;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 文件名（不含路径）
     */
    private String fileName;

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
}
