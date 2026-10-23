package com.azure.web;

import com.azure.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoImageMvcTest extends WebTestSupport {
    @Test
    void bundledDemoPhotosAreReadableWithoutLoginOrLocalUploads() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath:/menu-images/*.jpg");
        assertThat(resources).hasSizeGreaterThanOrEqualTo(40);
        for (var resource : resources) {
            byte[] bytes = resource.getContentAsByteArray();
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertThat(image).as(resource.getFilename()).isNotNull();
            assertThat(image.getWidth()).as(resource.getFilename()).isGreaterThanOrEqualTo(800);
            assertThat(image.getHeight()).as(resource.getFilename()).isGreaterThanOrEqualTo(600);
            mvc.perform(get("/menu-images/" + resource.getFilename()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("image/jpeg"))
                    .andExpect(content().bytes(bytes));
        }
    }
}
