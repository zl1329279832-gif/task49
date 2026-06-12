package com.xcs.wx.export.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 下载令牌
 *
 * @author xcs
 */
@Data
@AllArgsConstructor
public class DownloadToken {

    /**
     * 令牌
     */
    private String token;

    /**
     * 文件路径（规范化后的绝对路径）
     */
    private String filePath;

    /**
     * 关联任务ID
     */
    private String taskId;

    /**
     * 创建时间
     */
    private long createdAt;

    /**
     * 过期时间
     */
    private long expiresAt;
}
