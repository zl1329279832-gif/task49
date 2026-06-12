package com.xcs.wx.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 导出任务创建请求 DTO
 *
 * @author wx-dump-4j
 */
@Data
public class ExportTaskCreateDTO {

    /**
     * 微信账号（数据源），可选，默认当前用户
     */
    private String wxId;

    /**
     * 会话列表（StrTalker），必填，最多50个
     */
    private List<String> talkers;

    /**
     * 开始时间戳(秒)，可选
     */
    private Long startTime;

    /**
     * 结束时间戳(秒)，可选
     */
    private Long endTime;

    /**
     * 消息类型过滤，可选（空=全部）
     */
    private List<Integer> msgTypes;

    /**
     * 是否包含图片，默认false
     */
    private boolean includeImage;

    /**
     * 是否包含文件，默认false
     */
    private boolean includeFile;

    /**
     * 导出格式，目前只支持 "xlsx"
     */
    private String format;
}
