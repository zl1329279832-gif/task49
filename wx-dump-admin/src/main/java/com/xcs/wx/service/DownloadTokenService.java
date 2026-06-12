package com.xcs.wx.service;

import com.xcs.wx.domain.vo.DownloadTokenVO;

/**
 * 下载令牌服务
 *
 * @author xcs
 */
public interface DownloadTokenService {

    /**
     * 生成下载令牌
     *
     * @param taskId   任务ID
     * @param filePath 文件路径
     * @return 令牌VO
     */
    DownloadTokenVO generateToken(String taskId, String filePath);

    /**
     * 校验令牌并返回文件路径
     *
     * @param token 令牌
     * @return 文件路径
     */
    String validateAndGetPath(String token);

    /**
     * 撤销指定任务的所有令牌
     *
     * @param taskId 任务ID
     */
    void revokeTokensForTask(String taskId);

    /**
     * 清理过期令牌
     */
    void cleanExpiredTokens();
}
