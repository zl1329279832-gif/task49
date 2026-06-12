package com.xcs.wx.domain.vo;

import lombok.Data;

/**
 * 导出任务响应 VO
 *
 * @author wx-dump-4j
 */
@Data
public class ExportTaskVO {

    /**
     * 任务ID (UUID)
     */
    private String taskId;

    /**
     * 数据源账号
     */
    private String wxId;

    /**
     * 状态: PENDING / RUNNING / COMPLETED / FAILED / CANCELLED
     */
    private String status;

    /**
     * 进度百分比 0-100
     */
    private Integer progress;

    /**
     * 总消息数
     */
    private Integer totalMessages;

    /**
     * 已处理消息数
     */
    private Integer processedMessages;

    /**
     * 下载token（仅COMPLETED时返回）
     */
    private String downloadToken;

    /**
     * token剩余秒数（仅COMPLETED时返回）
     */
    private Long expiresIn;

    /**
     * 失败原因（仅FAILED时返回）
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
     * 导出文件名
     */
    private String fileName;
}
