package com.xcs.wx.export.cleanup;

import com.xcs.wx.service.DownloadTokenService;
import com.xcs.wx.service.ExportTaskService;
import com.xcs.wx.util.DirUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 导出文件定期清理任务
 *
 * @author xcs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportFileCleanupTask {

    private final DownloadTokenService downloadTokenService;
    private final ExportTaskService exportTaskService;

    /**
     * 文件保留时间：2小时
     */
    private static final long FILE_RETENTION_MS = 2 * 60 * 60 * 1000L;

    /**
     * 每10分钟执行一次清理
     */
    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        // 1. 清理过期下载令牌
        downloadTokenService.cleanExpiredTokens();

        // 2. 清理过期导出文件
        String exportDir = DirUtil.getExportDir("");
        File dir = new File(exportDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        long cutoff = System.currentTimeMillis() - FILE_RETENTION_MS;
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                if (file.delete()) {
                    log.info("清理过期导出文件: {}", file.getName());
                }
            }
        }

        // 3. 清理已完结的过期任务
        exportTaskService.cleanupOldTasks(cutoff);
    }
}
