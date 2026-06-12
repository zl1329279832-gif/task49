package com.xcs.wx.controller;

import com.xcs.wx.domain.dto.ExportTaskCreateDTO;
import com.xcs.wx.domain.vo.DownloadTokenVO;
import com.xcs.wx.domain.vo.ExportTaskVO;
import com.xcs.wx.domain.vo.ResponseVO;
import com.xcs.wx.service.DownloadTokenService;
import com.xcs.wx.service.ExportTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 导出任务 Controller
 *
 * @author xcs
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/export")
public class ExportTaskController {

    private final ExportTaskService exportTaskService;
    private final DownloadTokenService downloadTokenService;

    /**
     * 创建异步导出任务
     */
    @PostMapping("/task")
    public ResponseVO<ExportTaskVO> createTask(@RequestBody ExportTaskCreateDTO dto) {
        return ResponseVO.ok(exportTaskService.createTask(dto));
    }

    /**
     * 查询当前用户的所有任务
     */
    @GetMapping("/tasks")
    public ResponseVO<List<ExportTaskVO>> listTasks() {
        return ResponseVO.ok(exportTaskService.listTasks());
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/task/{taskId}")
    public ResponseVO<ExportTaskVO> getTask(@PathVariable String taskId) {
        return ResponseVO.ok(exportTaskService.getTask(taskId));
    }

    /**
     * 取消任务
     */
    @PostMapping("/task/{taskId}/cancel")
    public ResponseVO<Void> cancelTask(@PathVariable String taskId) {
        exportTaskService.cancelTask(taskId);
        return ResponseVO.ok(null);
    }

    /**
     * 重试失败的任务
     */
    @PostMapping("/task/{taskId}/retry")
    public ResponseVO<ExportTaskVO> retryTask(@PathVariable String taskId) {
        return ResponseVO.ok(exportTaskService.retryTask(taskId));
    }

    /**
     * 生成下载令牌
     */
    @PostMapping("/task/{taskId}/token")
    public ResponseVO<DownloadTokenVO> generateDownloadToken(@PathVariable String taskId) {
        return ResponseVO.ok(exportTaskService.generateDownloadToken(taskId));
    }

    /**
     * 安全下载（令牌验证，替代旧的路径直传下载）
     */
    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token) throws IOException {
        String filePath = downloadTokenService.validateAndGetPath(token);
        File file = new File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        String contentType = file.getName().endsWith(".zip")
                ? "application/zip"
                : "application/vnd.ms-excel";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    /**
     * 删除任务及关联文件
     */
    @DeleteMapping("/task/{taskId}")
    public ResponseVO<Void> deleteTask(@PathVariable String taskId) {
        exportTaskService.deleteTask(taskId);
        return ResponseVO.ok(null);
    }
}
