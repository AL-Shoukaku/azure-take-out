package com.azure.controller.admin;

import com.azure.result.Result;
import com.azure.service.FileUploadService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 通用 controller
 */
@RestController
@RequestMapping("/admin/common")
@Tag(name = "通用接口")
public class CommonController {

    @Autowired
    private FileUploadService fileUploadService;

    /**
     * 用于上传一个文件，参数名需要和前端一致
     * @param file
     * @return
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "文件上传")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        String path = fileUploadService.uploadAdminImage(file);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .build()
                .toUriString();
        return Result.success(url);
    }
}
