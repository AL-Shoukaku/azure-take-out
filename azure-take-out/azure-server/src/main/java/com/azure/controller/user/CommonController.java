package com.azure.controller.user;

import com.azure.result.Result;
import com.azure.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController("userCommonController")
@RequestMapping("/user/common")
@Tag(name = "用户端通用接口")
public class CommonController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传用户头像，返回图片地址，保存个人资料时写入 avatar")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        String path = fileUploadService.uploadUserAvatar(file);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .build()
                .toUriString();
        return Result.success(url);
    }
}
