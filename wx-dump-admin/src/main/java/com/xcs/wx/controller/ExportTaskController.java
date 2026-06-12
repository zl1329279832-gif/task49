package com.xcs.wx.controller;

import com.xcs.wx.domain.ExportTask;
import com.xcs.wx.domain.dto.ExportTaskCreateDTO;
import com.xcs.wx.domain.vo.ExportTaskVO;
import com.xcs.wx.domain.vo.ResponseVO;
import com.xcs.wx.exception.BizException;
import com.xcs.wx.service.DownloadTokenService;
import com.xcs.wx.service.ExportTaskExecutor;
import com.xcs.wx.service.ExportTaskManager;
import com.xcs.wx.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 导出任务 Controller
 *
 * @author wx-dump-4j
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/export/task")
public class ExportTaskController {

    private final ExportTaskManager exportTaskManager;
    private final ExportTaskExecutor exportTaskExecutor;
    private final DownloadTokenService downloadTokenService;
    private final UserService userService;

    /**
     * 创建导出任务
     *
     * @param dto 创建参数
     * @return 任务ID
     */
    @PostMapping("/create")
    public ResponseVO<String> createTask(@RequestBody ExportTaskCreateDTO dto) {
        // 参数验证
        validateCreateParams(dto);

        // 创建任务
        String taskId = exportTaskManager.createTask(dto);

        // 异步执行
        exportTaskExecutor.executeExport(taskId);

        return ResponseVO.ok(taskId);
    }

    /**
     * 查询任务进度
     *
     * @param taskId 任务ID
     * @return 任务信息
     */
    @GetMapping("/progress")
    public ResponseVO<ExportTaskVO> getProgress(@RequestParam String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new BizException(400, "任务ID不能为空");
        }
        return ResponseVO.ok(exportTaskManager.getTaskVO(taskId));
    }

    /**
     * 查询当前用户的所有任务
     *
     * @return 任务列表
     */
    @GetMapping("/list")
    public ResponseVO<List<ExportTaskVO>> listTasks() {
        String wxId = userService.currentUser();
        return ResponseVO.ok(exportTaskManager.getTasksByUser(wxId));
    }

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PutMapping("/cancel")
    public ResponseVO<String> cancelTask(@RequestParam String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new BizException(400, "任务ID不能为空");
        }
        exportTaskManager.requestCancel(taskId);
        return ResponseVO.ok("任务取消请求已发送");
    }

    /**
     * 重试失败任务
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PutMapping("/retry")
    public ResponseVO<String> retryTask(@RequestParam String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new BizException(400, "任务ID不能为空");
        }
        exportTaskManager.retryTask(taskId);
        // 重新异步执行
        exportTaskExecutor.executeExport(taskId);
        return ResponseVO.ok("任务已重新提交");
    }

    /**
     * 安全下载导出文件
     *
     * @param token 下载令牌
     * @return 文件流
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BizException(400, "下载令牌不能为空");
        }

        // 验证token并获取taskId
        String taskId = downloadTokenService.validateToken(token);
        ExportTask task = exportTaskManager.getTask(taskId);

        if (task.getFilePath() == null) {
            throw new BizException(404, "导出文件不存在");
        }

        // 路径安全校验：防止目录穿越攻击
        validateFilePath(task.getFilePath());

        File file = new File(task.getFilePath());
        if (!file.exists()) {
            throw new BizException(404, "导出文件不存在或已被清理");
        }

        Resource resource = new FileSystemResource(file);
        String encodedFileName = URLEncoder.encode(task.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentLength(file.length())
                .body(resource);
    }

    /**
     * 验证创建参数
     */
    private void validateCreateParams(ExportTaskCreateDTO dto) {
        if (dto.getTalkers() == null || dto.getTalkers().isEmpty()) {
            throw new BizException(400, "会话列表不能为空");
        }
        if (dto.getTalkers().size() > 50) {
            throw new BizException(400, "单次导出的会话数量不能超过50个");
        }
        if (dto.getFormat() != null && !"xlsx".equalsIgnoreCase(dto.getFormat())) {
            throw new BizException(400, "目前仅支持xlsx格式导出");
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && dto.getStartTime() > dto.getEndTime()) {
            throw new BizException(400, "开始时间不能晚于结束时间");
        }
    }

    /**
     * 验证文件路径安全性，防止目录穿越攻击
     */
    private void validateFilePath(String filePath) {
        Path exportDir = Paths.get(System.getProperty("user.dir"), "data", "export").toAbsolutePath().normalize();
        Path targetPath = Paths.get(filePath).toAbsolutePath().normalize();

        if (!targetPath.startsWith(exportDir)) {
            log.warn("Path traversal attempt detected: {}", filePath);
            throw new BizException(403, "非法的文件路径");
        }
    }
}
