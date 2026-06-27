package wiki.xmum.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageUpsertDTO {
    private String title;
    private String categorySlug;
    private String icon;
    private String description;
    private List<String> tags;
    private String content;
    private Integer sortOrder;
    private String status;
    private Integer version;
}
