package com.sky.web;

import com.sky.constant.MessageConstant;
import com.sky.controller.admin.CommonController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadFailureTest {
    @TempDir Path temp;
    @Test void uploadCreatesMissingDirectories() throws Exception {
        CommonController controller=new CommonController(); Path nested=temp.resolve("a/b"); ReflectionTestUtils.setField(controller,"uploadPath",nested.toString());
        assertThat(controller.upload(new MockMultipartFile("file","photo.png","image/png",new byte[]{1})).getCode()).isEqualTo(1);
        assertThat(Files.isDirectory(nested)).isTrue();
    }
    @Test void invalidStorageDirectoryUsesUploadFailureEnvelope() throws Exception {
        Path file=Files.write(temp.resolve("file"),new byte[]{1}); CommonController controller=new CommonController(); ReflectionTestUtils.setField(controller,"uploadPath",file.toString());
        assertThat(controller.upload(new MockMultipartFile("file","photo.png","image/png",new byte[]{1})).getMsg()).isEqualTo(MessageConstant.UPLOAD_FAILED);
    }
    @Test void inputStreamFailureUsesUploadFailureEnvelope() throws Exception {
        CommonController controller=new CommonController(); ReflectionTestUtils.setField(controller,"uploadPath",temp.toString());
        MultipartFile file=mock(MultipartFile.class); when(file.getOriginalFilename()).thenReturn("photo.png"); when(file.getInputStream()).thenThrow(new IOException("read failed"));
        assertThat(controller.upload(file).getCode()).isZero();
    }
}
