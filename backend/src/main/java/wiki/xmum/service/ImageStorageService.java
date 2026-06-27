package wiki.xmum.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import wiki.xmum.common.BizException;
import wiki.xmum.config.MinioProperties;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageStorageService {
    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/gif", "gif",
        "image/webp", "webp"
    );

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public ImageStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public UploadedImage upload(MultipartFile file) {
        validateBasic(file);

        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String extension = CONTENT_TYPES.get(contentType);
        try (InputStream raw = file.getInputStream();
             BufferedInputStream input = new BufferedInputStream(raw)) {
            input.mark(16);
            byte[] header = input.readNBytes(12);
            input.reset();
            if (!matchesSignature(contentType, header)) {
                throw new BizException("文件内容与图片类型不符");
            }

            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            String objectName = "images/" + datePath + "/" + UUID.randomUUID() + "." + extension;
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(input, file.getSize(), -1)
                    .contentType(contentType)
                    .build()
            );

            String url = stripTrailingSlash(properties.getPublicUrl()) + "/" + objectName;
            return new UploadedImage(objectName, url, "![](" + url + ")");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "图片上传失败");
        }
    }

    private void validateBasic(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择图片");
        }
        if (file.getSize() > properties.getMaxImageSize()) {
            throw new BizException("图片不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !CONTENT_TYPES.containsKey(contentType.toLowerCase(Locale.ROOT))) {
            throw new BizException("仅支持 JPG、PNG、GIF 和 WebP 图片");
        }
    }

    private boolean matchesSignature(String type, byte[] h) throws IOException {
        return switch (type) {
            case "image/jpeg" -> h.length >= 3
                && unsigned(h[0]) == 0xff && unsigned(h[1]) == 0xd8 && unsigned(h[2]) == 0xff;
            case "image/png" -> h.length >= 8
                && unsigned(h[0]) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && unsigned(h[4]) == 0x0d && unsigned(h[5]) == 0x0a
                && unsigned(h[6]) == 0x1a && unsigned(h[7]) == 0x0a;
            case "image/gif" -> h.length >= 6
                && h[0] == 'G' && h[1] == 'I' && h[2] == 'F'
                && h[3] == '8' && (h[4] == '7' || h[4] == '9') && h[5] == 'a';
            case "image/webp" -> h.length >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(500, "未配置图片公开访问地址");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record UploadedImage(String objectName, String url, String markdown) {}
}
