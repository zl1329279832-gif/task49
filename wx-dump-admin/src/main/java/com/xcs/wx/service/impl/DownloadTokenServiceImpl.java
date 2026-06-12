package com.xcs.wx.service.impl;

import com.xcs.wx.domain.vo.DownloadTokenVO;
import com.xcs.wx.exception.BizException;
import com.xcs.wx.export.model.DownloadToken;
import com.xcs.wx.service.DownloadTokenService;
import com.xcs.wx.util.DirUtil;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 下载令牌服务实现
 *
 * @author xcs
 */
@Service
public class DownloadTokenServiceImpl implements DownloadTokenService {

    /**
     * 令牌有效期：30分钟
     */
    private static final long TOKEN_TTL_MS = 30 * 60 * 1000L;

    /**
     * 令牌存储
     */
    private final ConcurrentHashMap<String, DownloadToken> tokenStore = new ConcurrentHashMap<>();

    @Override
    public DownloadTokenVO generateToken(String taskId, String filePath) {
        String canonicalExportDir = getCanonicalExportDir();
        String canonicalFilePath = getCanonicalPath(filePath);
        if (!canonicalFilePath.startsWith(canonicalExportDir)) {
            throw new BizException(-1, "非法的文件路径");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        DownloadToken downloadToken = new DownloadToken(token, canonicalFilePath, taskId, now, now + TOKEN_TTL_MS);
        tokenStore.put(token, downloadToken);

        DownloadTokenVO vo = new DownloadTokenVO();
        vo.setToken(token);
        vo.setExpiresAt(downloadToken.getExpiresAt());
        return vo;
    }

    @Override
    public String validateAndGetPath(String token) {
        DownloadToken downloadToken = tokenStore.get(token);
        if (downloadToken == null) {
            throw new BizException(-1, "无效的下载令牌");
        }
        if (System.currentTimeMillis() > downloadToken.getExpiresAt()) {
            tokenStore.remove(token);
            throw new BizException(-1, "下载令牌已过期");
        }
        // 再次校验路径安全性
        String canonicalExportDir = getCanonicalExportDir();
        if (!downloadToken.getFilePath().startsWith(canonicalExportDir)) {
            tokenStore.remove(token);
            throw new BizException(-1, "非法的文件路径");
        }
        return downloadToken.getFilePath();
    }

    @Override
    public void revokeTokensForTask(String taskId) {
        tokenStore.entrySet().removeIf(entry -> entry.getValue().getTaskId().equals(taskId));
    }

    @Override
    public void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        tokenStore.entrySet().removeIf(entry -> now > entry.getValue().getExpiresAt());
    }

    private String getCanonicalExportDir() {
        try {
            return new File(DirUtil.getExportDir("")).getCanonicalPath();
        } catch (IOException e) {
            throw new BizException(-1, "导出目录异常");
        }
    }

    private String getCanonicalPath(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            throw new BizException(-1, "非法的文件路径");
        }
    }
}
