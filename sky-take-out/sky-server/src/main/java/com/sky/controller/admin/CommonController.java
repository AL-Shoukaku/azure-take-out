package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 通用 controller
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {

    @Value("${sky.upload-path}")
    private String uploadPath;

    /**
     * 用于上传一个文件，参数名需要和前端一致
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传：{}", file);
        // 防止重名，使用UUID生成文件名
        try {
            // 获取原始文件名，截取后缀名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

            // 获取存储位置
            Path directory = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(directory);


            // 防止重名，使用UUID生成文件名，加上后缀名
            String fileName = UUID.randomUUID() + extension;
            Path target = directory.resolve(fileName);  // 获取目标文件的本地存储路径

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            // 设置好访问路径，返回给前端
            String url = "http://localhost:8080/images/" + fileName;
            return Result.success(url);
        } catch (IOException e) {
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }
}
