package com.sky.web;

import com.sky.constant.MessageConstant;
import com.sky.support.ImageFixtures;
import com.sky.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocalUploadMvcTest extends WebTestSupport {
    private String upload(String side, String name, byte[] content) throws Exception {
        var request = multipart("/" + side + "/common/upload")
                .file(new MockMultipartFile("file", name, "application/octet-stream", content));
        String body = successful(side.equals("admin") ? admin(request) : user(request))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").asText();
    }

    @ParameterizedTest
    @CsvSource({"admin,png,photo.png", "admin,jpeg,photo.JPEG", "user,png,../../photo.png", "user,jpeg,photo.png"})
    void uploadsUseSeparateDirectoriesAndActualImageFormat(String side, String format, String name) throws Exception {
        byte[] image = ImageFixtures.image(format, 0xff0000);
        String url = upload(side, name, image);
        URI uri = URI.create(url);
        String extension = format.equals("jpeg") ? ".jpg" : ".png";
        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.getHost()).isEqualTo("localhost");
        assertThat(uri.getPath()).startsWith("/images/" + side + "/").endsWith(extension);
        String filename = Path.of(uri.getPath()).getFileName().toString();
        assertThat(filename.substring(0, 36)).matches("[0-9a-f-]{36}");
        assertThat(Files.readAllBytes(UPLOADS.resolve(side).resolve(filename))).isEqualTo(image);
        assertThat(UPLOADS.resolve(filename)).doesNotExist();
        mvc.perform(get(uri.getPath())).andExpect(status().isOk()).andExpect(content().bytes(image));
    }

    @Test
    void returnedImageUrlUsesActualRequestOrigin() throws Exception {
        var request = user(multipart("/user/common/upload").file(
                new MockMultipartFile("file", "avatar.png", "image/png", ImageFixtures.png())));
        request.with(servlet -> {
            servlet.setScheme("https");
            servlet.setServerName("delivery.example");
            servlet.setServerPort(8443);
            return servlet;
        });
        String body = successful(request).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).path("data").asText()).startsWith("https://delivery.example:8443/images/user/");
    }

    @Test
    void sameOriginalFilenameDoesNotOverwritePreviousUpload() throws Exception {
        String first = upload("user", "avatar.png", ImageFixtures.image("png", 0xff0000));
        String second = upload("user", "avatar.png", ImageFixtures.image("png", 0x0000ff));
        assertThat(first).isNotEqualTo(second);
        mvc.perform(get(URI.create(first).getPath())).andExpect(content().bytes(ImageFixtures.image("png", 0xff0000)));
        mvc.perform(get(URI.create(second).getPath())).andExpect(content().bytes(ImageFixtures.image("png", 0x0000ff)));
    }

    @ParameterizedTest @ValueSource(strings = {"admin", "user"})
    void missingOrOppositeRoleTokenCannotUpload(String side) throws Exception {
        var noToken = multipart("/" + side + "/common/upload")
                .file(new MockMultipartFile("file", "avatar.png", "image/png", ImageFixtures.png()));
        mvc.perform(noToken).andExpect(status().isUnauthorized());
        var opposite = multipart("/" + side + "/common/upload")
                .file(new MockMultipartFile("file", "avatar.png", "image/png", ImageFixtures.png()));
        mvc.perform(side.equals("admin") ? user(opposite) : admin(opposite)).andExpect(status().isUnauthorized());
        try (var files = Files.list(UPLOADS)) {
            assertThat(files.count()).isZero();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"admin", "user"})
    void emptyInvalidAndOversizedImagesReturnBusinessErrorsWithoutWritingFiles(String side) throws Exception {
        byte[][] files = {new byte[0], "<script>alert(1)</script>".getBytes(), new byte[1024 * 1024 + 1]};
        String[] messages = {MessageConstant.UPLOAD_EMPTY, MessageConstant.UPLOAD_TYPE_ERROR, MessageConstant.UPLOAD_SIZE_EXCEEDED};
        for (int i = 0; i < files.length; i++) {
            var request = multipart("/" + side + "/common/upload")
                    .file(new MockMultipartFile("file", "fake.png", "image/png", files[i]));
            mvc.perform(side.equals("admin") ? admin(request) : user(request))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.msg").value(messages[i]));
        }
        try (var stored = Files.list(UPLOADS)) {
            assertThat(stored.count()).isZero();
        }
    }

    @Test
    void unsupportedActualImageFormatIsRejected() throws Exception {
        mvc.perform(user(multipart("/user/common/upload").file(new MockMultipartFile(
                        "file", "fake.png", "image/png", ImageFixtures.image("gif", 0xff0000)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.UPLOAD_TYPE_ERROR));
    }

    @Test
    void oldAdminImageAddressStillReadsMigratedFile() throws Exception {
        Files.createDirectories(UPLOADS.resolve("admin"));
        byte[] image = ImageFixtures.png();
        Files.write(UPLOADS.resolve("admin/legacy.png"), image);
        for (String path : new String[]{"/images/legacy.png", "/images/admin/legacy.png"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().bytes(image));
        }
    }

    @Test
    void missingImagesAndTraversalCannotExposeOtherFiles() throws Exception {
        for (String path : new String[]{"/images/missing.png", "/images/user/missing.png",
                "/images/../application.yml", "/images/user/../../application.yml"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void wrongFilePartNameReturnsBadRequest() throws Exception {
        mvc.perform(user(multipart("/user/common/upload").file(new MockMultipartFile(
                "avatar", "avatar.png", "image/png", ImageFixtures.png())))).andExpect(status().isBadRequest());
    }
}
