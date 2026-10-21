package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private static final long MAX_IMAGE_SIZE = 1024 * 1024;

    @Value("${sky.upload-path}")
    private String uploadPath;

    @Override
    public String uploadAdminImage(MultipartFile file) {
        return saveImage(file, "admin");
    }

    @Override
    public String uploadUserAvatar(MultipartFile file) {
        return saveImage(file, "user");
    }

    private String saveImage(MultipartFile file, String directoryName) {
        if (file.isEmpty()) {
            throw new BaseException(MessageConstant.UPLOAD_EMPTY);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BaseException(MessageConstant.UPLOAD_SIZE_EXCEEDED);
        }

        try {
            String extension = detectImageExtension(file);
            Path directory = Path.of(uploadPath).toAbsolutePath().normalize().resolve(directoryName);
            Files.createDirectories(directory);
            // 文件名和后缀由服务端生成，不使用客户端提交的路径或后缀。
            String filename = UUID.randomUUID() + extension;
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, directory.resolve(filename));
            }
            return "/images/" + directoryName + "/" + filename;
        } catch (IOException e) {
            log.error("图片上传失败，存储目录：{}", directoryName, e);
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }
    }

    private String detectImageExtension(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new BaseException(MessageConstant.UPLOAD_TYPE_ERROR);
            }

            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format)) {
                    throw new BaseException(MessageConstant.UPLOAD_TYPE_ERROR);
                }
                reader.setInput(imageInput, true, true);
                // 读取图片头中的尺寸，拒绝损坏的图片头，不在服务端完整解码大图。
                reader.getWidth(0);
                reader.getHeight(0);
                return "JPEG".equalsIgnoreCase(format) ? ".jpg" : ".png";
            } finally {
                reader.dispose();
            }
        }
    }
}
