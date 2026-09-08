// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
package com.td.czghagent.infrastructure.storage;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.ProcessedImage;
import com.td.czghagent.domain.port.ImageProcessor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class JavaImageProcessor implements ImageProcessor {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("png", "jpeg", "jpg");
    private static final int MAX_EDGE_PX = 12_000;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final int AVATAR_EDGE_PX = 512;

    /**
     * <p><b>Preconditions:</b>content为用户上传且尚未信任的图片字节。</p>
     * <p><b>Side Effects:</b>在内存中解码、缩放并重新编码PNG，不写入文件。</p>
     * <p><b>Error Semantics:</b>无法完整解码或超出像素限制时返回IMAGE_INVALID。</p>
     */
    @Override
    public ProcessedImage normalizeAvatar(byte[] content) {
        DecodedImage source = decode(content);
        int sourceWidth = source.image().getWidth();
        int sourceHeight = source.image().getHeight();
        double ratio = Math.min(1D, (double) AVATAR_EDGE_PX / Math.max(sourceWidth, sourceHeight));
        int width = Math.max(1, (int) Math.round(sourceWidth * ratio));
        int height = Math.max(1, (int) Math.round(sourceHeight * ratio));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source.image(), 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(target, "png", output)) {
                throw invalidImage();
            }
            return new ProcessedImage("avatar.png", "image/png", width, height, output.toByteArray());
        } catch (IOException exception) {
            throw invalidImage();
        }
    }

    private DecodedImage decode(byte[] content) {
        if (content == null || content.length == 0) {
            throw invalidImage();
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!SUPPORTED_FORMATS.contains(format)) {
                    throw new BusinessException(
                            "IMAGE_TYPE_UNSUPPORTED", "仅支持 PNG 或 JPEG 图片", 400
                    );
                }
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
                String mediaType = "png".equals(format) ? "image/png" : "image/jpeg";
                return new DecodedImage(mediaType, image);
            } finally {
                reader.dispose();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidImage();
        }
    }

    private void validateDimensions(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_EDGE_PX || height > MAX_EDGE_PX
                || (long) width * height > MAX_PIXELS) {
            throw new BusinessException(
                    "IMAGE_DIMENSIONS_EXCEEDED", "图片尺寸过大，请压缩后重新上传", 400
            );
        }
    }

    private BusinessException invalidImage() {
        return new BusinessException("IMAGE_INVALID", "文件不是可读取的有效图片", 400);
    }

    private record DecodedImage(String mediaType, BufferedImage image) {
    }
}

