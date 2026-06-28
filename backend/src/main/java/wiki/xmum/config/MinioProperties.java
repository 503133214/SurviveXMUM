package wiki.xmum.config;

import lombok.Data;

@Data
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket = "wiki";
    private String publicUrl;
    private long maxImageSize = 10 * 1024 * 1024;
}
