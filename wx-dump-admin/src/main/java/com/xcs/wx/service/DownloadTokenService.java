package com.xcs.wx.service;

/**
 * 下载令牌服务接口
 *
 * @author wx-dump-4j
 */
public interface DownloadTokenService {

    /**
     * 生成下载令牌
     *
     * @param taskId 任务ID
     * @return UUID 令牌字符串
     */
    String generateToken(String taskId);

    /**
     * 验证下载令牌并返回对应的任务ID
     *
     * @param token 令牌字符串
     * @return 任务ID
     * @throws com.xcs.wx.exception.BizException 令牌无效或已过期时抛出
     */
    String validateToken(String token);

    /**
     * 移除令牌
     *
     * @param token 令牌字符串
     */
    void removeToken(String token);

    /**
     * 清理过期令牌
     */
    void cleanupExpiredTokens();
}
