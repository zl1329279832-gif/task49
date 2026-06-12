package com.xcs.wx.controller;

import com.xcs.wx.util.DirUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 导出 Controller（已废弃，请使用 ExportTaskController 的安全令牌下载接口）
 *
 * @author xcs
 * @date 2024年01月05日 15时56分
 * @deprecated 使用 {@link ExportTaskController} 的 /api/export/download/{token} 接口替代
 **/
@Deprecated
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/export-legacy")
public class ExportController {

    /**
     * 下载文件（已加入目录穿越防护）
     */
    @GetMapping("download")
    public ResponseEntity<Resource> download(@RequestParam String path) throws IOException {
        // 目录穿越防护：校验文件路径必须在导出目录内
        File requestedFile = new File(path).getCanonicalFile();
        File exportDir = new File(DirUtil.getExportDir("")).getCanonicalFile();
        if (!requestedFile.toPath().startsWith(exportDir.toPath())) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get(requestedFile.getPath());
        Resource resource = new FileSystemResource(filePath.toFile());

        // 处理文件不存在的情况
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String encodedFilename = URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8.name()).replace("+", "%20");
        String contentDisposition = "attachment; filename*=UTF-8''" + encodedFilename;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .header("Content-Disposition", contentDisposition)
                .body(resource);
    }
}
