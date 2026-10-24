package com.azure.web;

import com.azure.constant.MessageConstant;
import com.azure.exception.BaseException;
import com.azure.handler.GlobalExceptionHandler;
import com.azure.service.impl.FileUploadServiceImpl;
import com.azure.support.ImageFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadFailureTest {
    @TempDir Path temp;

    private FileUploadServiceImpl storage(Path directory) {
        var storage = new FileUploadServiceImpl();
        ReflectionTestUtils.setField(storage, "uploadPath", directory.toString());
        return storage;
    }

    @Test
    void uploadCreatesMissingRootAndRoleDirectories() throws Exception {
        Path root = temp.resolve("a/b");
        var storage = storage(root);
        var file = new MockMultipartFile("file", "photo.png", "image/png", ImageFixtures.png());
        assertThat(storage.uploadAdminImage(file)).startsWith("/images/admin/");
        assertThat(storage.uploadUserAvatar(file)).startsWith("/images/user/");
        assertThat(root.resolve("admin")).isDirectory();
        assertThat(root.resolve("user")).isDirectory();
    }

    @Test
    void invalidStorageDirectoryReturnsUploadBusinessError() throws Exception {
        Path file = Files.write(temp.resolve("file"), new byte[]{1});
        var storage = storage(file);
        var image = new MockMultipartFile("file", "photo.png", "image/png", ImageFixtures.png());
        assertThatThrownBy(() -> storage.uploadUserAvatar(image))
                .isInstanceOf(BaseException.class).hasMessage(MessageConstant.UPLOAD_FAILED);
    }

    @Test
    void inputStreamFailureReturnsUploadBusinessError() throws Exception {
        var storage = storage(temp);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("read failed"));
        assertThatThrownBy(() -> storage.uploadAdminImage(file))
                .isInstanceOf(BaseException.class).hasMessage(MessageConstant.UPLOAD_FAILED);
    }

    @Test
    void malformedImageHeaderReturnsBusinessErrorInsteadOfServerError() {
        var storage = storage(temp);
        byte[] truncatedPng = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        var image = new MockMultipartFile("file", "broken.png", "image/png", truncatedPng);
        assertThatThrownBy(() -> storage.uploadUserAvatar(image)).isInstanceOf(BaseException.class);
    }

    @Test
    void servletUploadLimitUsesTheSameErrorEnvelope() {
        var result = new GlobalExceptionHandler().uploadSizeExceeded(new MaxUploadSizeExceededException(1024 * 1024));
        assertThat(result.getCode()).isZero();
        assertThat(result.getMsg()).isEqualTo(MessageConstant.UPLOAD_SIZE_EXCEEDED);
    }
}
