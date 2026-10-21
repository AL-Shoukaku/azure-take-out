package com.sky.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传与本地存储。
 */
public interface FileUploadService {

    /**
     * 上传管理端图片，返回相对访问路径。
     */
    String uploadAdminImage(MultipartFile file);

    /**
     * 上传用户头像，返回相对访问路径。
     */
    String uploadUserAvatar(MultipartFile file);
}
