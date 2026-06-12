package com.xcs.wx.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建导出任务请求DTO
 *
 * @author xcs
 */
@Data
public class ExportTaskCreateDTO {

    /**
     * 导出类型：MSG, CONTACT, CHATROOM
     */
    private String exportType;

    /**
     * 会话ID（MSG导出时必填）
     */
    private String talker;

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
}
