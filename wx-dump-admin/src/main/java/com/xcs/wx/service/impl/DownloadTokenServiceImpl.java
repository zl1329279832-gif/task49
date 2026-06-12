package com.xcs.wx.service.impl;

import com.xcs.wx.exception.BizException;
import com.xcs.wx.service.DownloadTokenService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 下载令牌服务实现
 *
 * @author wx-dump-4j
 */
@Slf4j
@Service
public class DownloadTokenServiceImpl implements DownloadTokenService {

    /**
     * 令牌过期时间：30分钟
     */
    private static final long TOKEN_EXPIRE_MS = 30 * 60 * 1000L;

    /**
     * 令牌存储: token -> TokenInfo
     */
    private final ConcurrentHashMap<String, TokenInfo> tokenStore = new ConcurrentHashMap<>();

    @Override
    public String generateToken(String taskId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long expireAt = System.currentTimeMillis() + TOKEN_EXPIRE_MS;
        tokenStore.put(token, new TokenInfo(taskId, expireAt));
        log.info("Generated download token for task [{}], token=[{}]", taskId, token);
        return token;
    }

    @Override
    public String validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BizException(400, "下载令牌不能为空");
        }
        TokenInfo info = tokenStore.get(token);
        if (info == null) {
            throw new BizException(404, "下载令牌无效或已过期");
        }
        if (System.currentTimeMillis() > info.getExpireAt()) {
            tokenStore.remove(token);
            throw new BizException(401, "下载令牌已过期，请重新获取");
        }
        return info.getTaskId();
    }

    @Override
    public void removeToken(String token) {
        if (token != null) {
            tokenStore.remove(token);
        }
    }

    @Override
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TokenInfo>> iterator = tokenStore.entrySet().iterator();
        int removed = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, TokenInfo> entry = iterator.next();
            if (now > entry.getValue().getExpireAt()) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired download tokens", removed);
        }
    }

    /**
     * 令牌信息
     */
    @Data
    private static class TokenInfo {
        private final String taskId;
        private final long expireAt;

        public TokenInfo(String taskId, long expireAt) {
            this.taskId = taskId;
            this.expireAt = expireAt;
        }
    }
}
