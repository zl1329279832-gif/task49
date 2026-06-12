package com.xcs.wx.domain.vo;

import lombok.Data;

/**
 * 下载令牌响应VO
 *
 * @author xcs
 */
@Data
public class DownloadTokenVO {

    /**
     * 下载令牌
     */
    private String token;

    /**
     * 过期时间
     */
    private long expiresAt;
}
