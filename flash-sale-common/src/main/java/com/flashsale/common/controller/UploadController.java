package com.flashsale.common.controller;

import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.result.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传控制器 — 仅管理员可上传图片
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final String UPLOAD_DIR = "./uploads/";

    @PostMapping
    public Result<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Role") String role) {

        // 校验管理员权限
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return Result.error(ErrorCode.FORBIDDEN, "仅管理员可上传文件");
        }

        // 校验文件是否为空
        if (file.isEmpty()) {
            return Result.error(ErrorCode.BAD_REQUEST, "文件不能为空");
        }

        // 获取原始文件名和扩展名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return Result.error(ErrorCode.BAD_REQUEST, "文件格式不正确");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.error(ErrorCode.BAD_REQUEST, "仅支持图片文件（jpg/jpeg/png/gif/webp/bmp）");
        }

        // 生成唯一文件名
        String newFilename = UUID.randomUUID().toString() + "." + extension;

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path targetPath = uploadPath.resolve(newFilename);
            file.transferTo(targetPath.toFile());

            String url = "/uploads/" + newFilename;
            return Result.success(url);
        } catch (IOException e) {
            return Result.error(ErrorCode.INTERNAL_ERROR, "文件上传失败：" + e.getMessage());
        }
    }
}
