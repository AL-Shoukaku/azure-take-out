package com.sky.web;

import com.sky.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocalUploadMvcTest extends WebTestSupport {
    private String upload(String name,byte[] content) throws Exception {
        String body = successful(admin(multipart("/admin/common/upload").file(new MockMultipartFile("file",name,"image/png",content))))
                .andReturn().getResponse().getContentAsString(); return json.readTree(body).get("data").asText();
    }
    @ParameterizedTest @ValueSource(strings={"photo.png","photo.jpg","photo.JPEG","../../photo.png"})
    void uploadUsesUuidPreservesExtensionAndImageUrlReadsSavedBytes(String name) throws Exception {
        byte[] bytes={1,2,3,4}; String url=upload(name,bytes); String suffix=name.substring(name.lastIndexOf('.'));
        assertThat(url).startsWith("http://localhost:8080/images/").endsWith(suffix);
        String filename=url.substring(url.lastIndexOf('/')+1); assertThat(filename.substring(0,36)).matches("[0-9a-f-]{36}");
        assertThat(Files.readAllBytes(UPLOADS.resolve(filename))).isEqualTo(bytes);
        mvc.perform(get("/images/"+filename)).andExpect(status().isOk()).andExpect(content().bytes(bytes));
    }
    @Test void sameOriginalFilenameDoesNotOverwritePreviousUpload() throws Exception {
        String first=upload("photo.png",new byte[]{1}); String second=upload("photo.png",new byte[]{2}); assertThat(first).isNotEqualTo(second);
        try (java.util.stream.Stream<Path> files=Files.list(UPLOADS)) { assertThat(files.count()).isEqualTo(2); }
    }
    @Test void uploadRejectsMissingAuthenticationWithoutWritingFile() throws Exception {
        mvc.perform(multipart("/admin/common/upload").file(new MockMultipartFile("file","photo.png","image/png",new byte[]{1}))).andExpect(status().isUnauthorized());
        try (java.util.stream.Stream<Path> files=Files.list(UPLOADS)) { assertThat(files.count()).isZero(); }
    }
    @Test void missingImagesReturn404AndTraversalCannotExposeExternalFile() throws Exception {
        mvc.perform(get("/images/missing.png")).andExpect(status().isNotFound());
        mvc.perform(get("/images/../application.yml")).andExpect(status().isNotFound());
    }
}
