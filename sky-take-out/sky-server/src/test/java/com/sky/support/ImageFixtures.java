package com.sky.support;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** 有效图片和临时上传目录清理，避免依赖真实 uploads。 */
public final class ImageFixtures {
    private ImageFixtures() {}

    public static byte[] image(String format, int color) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IOException("Unsupported fixture image format: " + format);
            }
            return output.toByteArray();
        }
    }

    public static byte[] png() throws IOException {
        return image("png", 0xff0000);
    }

    public static void clearDirectory(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(root)) {
                    Files.delete(path);
                }
            }
        }
    }
}
