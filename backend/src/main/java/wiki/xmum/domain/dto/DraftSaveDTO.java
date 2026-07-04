package wiki.xmum.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class DraftSaveDTO {
    private Long id;              // 已有草稿则带上（upsert）
    private String type;          // CREATE / UPDATE
    private String path;          // UPDATE 草稿的目标页路径
    private String categorySlug;
    private String title;
    private String icon;
    private String description;
    private List<String> tags;
    private String content;
    private Integer baseVersion;
}
